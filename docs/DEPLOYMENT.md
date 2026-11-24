# Deployment on Render

Set up the production database first: [DATABASE_SETUP.md](DATABASE_SETUP.md).

None of these services should ask for a credit card. If one does, stop and tell the team; the app also runs on a laptop with the same database.

## 1. Cloudinary (report photos)

1. Sign up for the free plan at <https://cloudinary.com>.
2. Open **Settings > API Keys** and copy the API environment variable. It looks like `cloudinary://KEY:SECRET@CLOUD_NAME`.
3. Use it as `CLOUDINARY_URL`.

## 2. Brevo (password reset email)

1. Sign up for the free plan at <https://www.brevo.com> (300 emails a day).
2. Add and verify a sender email address. Use it as `MAIL_FROM`.
3. Open **SMTP & API > SMTP** and create an SMTP key.

Render's free plan blocks ports 25, 465 and 587, so the app uses Brevo's port **2525**.

## 3. Render (hosting)

1. Sign up at <https://render.com> with GitHub.
2. Choose **New > Web Service** and select this repository. Render finds the `Dockerfile`.
3. Choose region **Singapore** and instance type **Free**.
4. Under **Secret Files**, add `firebase-service-account.json` with the contents of your key.
5. Add the environment variables:

   | Name | Value |
   |---|---|
   | `FIREBASE_CREDENTIALS_PATH` | `/etc/secrets/firebase-service-account.json` |
   | `ADMIN_USERNAME` | your first admin's username |
   | `ADMIN_EMAIL` | your first admin's email |
   | `ADMIN_PASSWORD` | 8 to 72 characters; remove it after the first sign-in |
   | `CLOUDINARY_URL` | from step 1 |
   | `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` |
   | `SPRING_MAIL_PORT` | `2525` |
   | `SPRING_MAIL_USERNAME` | your Brevo SMTP login |
   | `SPRING_MAIL_PASSWORD` | your Brevo SMTP key |
   | `MAIL_FROM` | your verified sender |

   Render sets `PORT` and `RENDER_EXTERNAL_URL` itself.

6. Deploy and open the HTTPS address.

## 4. Live smoke test (TC-15)

- [ ] The dashboard opens over HTTPS
- [ ] A report without a photo appears first, with the placeholder
- [ ] A report with a photo shows the photo
- [ ] Each category pill works (needs the Firestore index)
- [ ] An admin can sign in, change a status and fetch news
- [ ] **Forgot Password?** delivers an email and the link works once

If no reset email arrives, read the Render log. A timeout means Render also blocks port 2525.

## Before a demo

A free instance sleeps after 15 minutes without visitors and needs about a minute to wake up. Open the site at least 15 minutes before presenting.

## Rollback

- **Bad release:** in Render, redeploy the last working commit.
- **Render is down:** run the app on a laptop with the production key (see DATABASE_SETUP.md).
- **News sources are down:** stored news stays visible.
