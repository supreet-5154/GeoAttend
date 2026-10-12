# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

## Authentication

GeoAttend uses email and password authentication with JWT authorization.
New members can register via the sign-up form on the login page. Submitted registrations remain pending until reviewed and approved by club staff (`ADMIN` or `CLUB_USER`) from the **Members → Registration Requests** queue.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.

## Automatic geofence attendance

Members sign in and keep the GeoAttend tab open during an active session. The member portal requests browser location automatically and divides each session into ten check slots (for example, every minute in a 10-minute session or every six minutes in a one-hour session). The backend stores each check's timestamp and inside/outside result, while precise coordinates are available only to ADMIN accounts in the audit view.

At session end, the backend finalizes attendance when at least 80% of the expected checks were inside the geofence. Lower results are placed in the staff Attendance review queue; ADMIN and CLUB_USER accounts can record a manual override there. The backend creates the tracking tables on startup using `src/main/resources/schema.sql`.

Browser location requires permission and a secure context (`localhost` during development or HTTPS when deployed). Browsers can throttle timers in background tabs, so members should keep the attendance tab open and active. Missed checks count against the 80% threshold.
