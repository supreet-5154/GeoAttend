import { useEffect, useRef } from "react";
import attendanceService from "../services/attendanceService";
import sessionService from "../services/sessionService";

const FIRST_CHECK_RETRY_MS = 60 * 1000;

const readDeviceLocation = (timeout) => new Promise((resolve, reject) => {
  if (!navigator.geolocation) {
    reject(new Error("This browser does not support location access."));
    return;
  }

  navigator.geolocation.getCurrentPosition(
    ({ coords }) => resolve({
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracyMeters: coords.accuracy,
    }),
    (error) => reject(error),
    { enableHighAccuracy: true, maximumAge: 0, timeout },
  );
});

export default function GeoAttendanceTracker({ showToast }) {
  const showToastRef = useRef(showToast);
  const inFlight = useRef(new Set());
  const nextAttemptAt = useRef(new Map());
  const lastNoticeAt = useRef(new Map());
  const trackingStartedNotices = useRef(new Set());
  const tickInProgress = useRef(false);

  useEffect(() => {
    showToastRef.current = showToast;
  }, [showToast]);

  useEffect(() => {
    let stopped = false;
    let timer;

    const notifyAtMostEvery = (key, message, type = "info") => {
      const now = Date.now();
      const lastNotice = lastNoticeAt.current.get(key) || 0;
      if (now - lastNotice >= 10 * 60 * 1000) {
        lastNoticeAt.current.set(key, now);
        showToastRef.current(message, type);
      }
    };

    const checkSession = async (session, tracking) => {
      const sessionKey = String(session.id);
      if (inFlight.current.has(sessionKey)) return;

      const now = Date.now();
      const sessionStart = new Date(session.startTime).getTime();
      const sessionEnd = new Date(session.endTime).getTime();
      const sessionIntervalSeconds = Number(tracking?.checkIntervalSeconds)
        || Math.floor((sessionEnd - sessionStart) / 10000);
      const checkIntervalMs = Math.max(1000, sessionIntervalSeconds * 1000);
      const hasRecordedSample = Number(tracking?.totalChecks) > 0;
      const lastCheckDueAt = hasRecordedSample
        ? new Date(tracking.lastCheckAt).getTime() + checkIntervalMs
        : 0;
      const dueAt = Math.max(lastCheckDueAt, nextAttemptAt.current.get(sessionKey) || 0);
      if (now < dueAt) return dueAt - now;

      inFlight.current.add(sessionKey);
      try {
        if (!tracking) {
          await attendanceService.startMyTracking({ sessionId: session.id });
        }

        const locationTimeout = Math.min(20000, Math.max(1000, checkIntervalMs - 500));
        const location = await readDeviceLocation(locationTimeout);
        if (stopped) return;

        const result = await attendanceService.recordLocationCheck({
          sessionId: session.id,
          ...location,
        });
        const nextCheckAt = result.nextCheckAt ? new Date(result.nextCheckAt).getTime() : Date.now() + checkIntervalMs;
        nextAttemptAt.current.set(sessionKey, nextCheckAt);
        if (result.sampleRecorded) {
          lastNoticeAt.current.delete(`location-${sessionKey}`);
          if (result.insideGeofence && result.status === "TRACKING" && !trackingStartedNotices.current.has(sessionKey)) {
            trackingStartedNotices.current.add(sessionKey);
            showToastRef.current("You’re inside the geofence. Attendance tracking has started.", "success");
          } else if (!result.insideGeofence) {
            const distance = Number(result.distanceMeters).toFixed(1);
            const radius = result.radiusMeters;
            const accuracy = result.accuracyMeters == null ? "GPS accuracy unavailable" : `GPS accuracy ±${Number(result.accuracyMeters).toFixed(0)} m`;
            notifyAtMostEvery(
              `outside-${sessionKey}`,
              `Location ${distance} m from center; fence radius ${radius} m. ${accuracy}. This check is recorded.`,
              "error",
            );
          }
        } else if (!result.insideGeofence) {
          notifyAtMostEvery(`not-due-${sessionKey}`, `Next location check is scheduled for ${new Date(nextCheckAt).toLocaleTimeString()}.`, "info");
        }
        return Math.max(250, nextCheckAt - Date.now());
      } catch (error) {
        const retryDelay = Math.min(FIRST_CHECK_RETRY_MS, checkIntervalMs);
        const retryAt = Date.now() + retryDelay;
        nextAttemptAt.current.set(sessionKey, retryAt);
        notifyAtMostEvery(
          `location-${sessionKey}`,
          error.code === 1
            ? "Allow location access in your browser to track attendance automatically."
            : error.message || "Unable to check your current location. Keep this page open and try again.",
          "error",
        );
        return retryDelay;
      } finally {
        inFlight.current.delete(sessionKey);
      }
    };

    const tick = async () => {
      if (stopped || tickInProgress.current) return;
      tickInProgress.current = true;
      let nextDelay = 30000;
      try {
        const [sessions, trackingList, attendanceList] = await Promise.all([
          sessionService.listSessions(),
          attendanceService.getMyTracking(),
          attendanceService.getMyAttendance(),
        ]);
        if (stopped) return;

        const now = Date.now();
        const activeSessions = sessions.filter((session) => {
          const startsAt = new Date(session.startTime).getTime();
          const endsAt = new Date(session.endTime).getTime();
          return now >= startsAt && now < endsAt && Boolean(session.locationEnabled ?? true);
        });

        const sessionDelays = await Promise.all(activeSessions.map((session) => {
          if (attendanceList.some((record) => String(record.sessionId) === String(session.id))) {
            return nextDelay;
          }
          const tracking = trackingList.find((item) => String(item.sessionId) === String(session.id));
          if (tracking && !["TRACKING", "WAITING_FOR_GEOFENCE"].includes(tracking.status)) return nextDelay;
          return checkSession(session, tracking);
        }));
        sessionDelays.forEach((delay) => {
          if (Number.isFinite(delay)) nextDelay = Math.min(nextDelay, delay);
        });

        const nextSessionStart = sessions
          .map((session) => new Date(session.startTime).getTime())
          .filter((start) => start > now)
          .sort((left, right) => left - right)[0];
        if (nextSessionStart) nextDelay = Math.min(nextDelay, nextSessionStart - now);
      } catch (error) {
        notifyAtMostEvery("tracking-service", error.message || "Attendance tracking is temporarily unavailable.", "error");
      } finally {
        tickInProgress.current = false;
        if (!stopped) timer = window.setTimeout(tick, Math.max(250, nextDelay));
      }
    };

    tick();
    const handleVisibility = () => {
      if (document.visibilityState === "visible") tick();
    };
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      stopped = true;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, []);

  return null;
}