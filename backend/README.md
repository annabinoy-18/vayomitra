# Backend setup

The backend is a Java 21/Maven application using JDBC and PostgreSQL. Copy `.env.example` to `.env` and set the database connection values before starting the API.

Demo accounts are disabled by default. To seed a local demo elder and volunteer into an empty database, set `SEED_DEMO_DATA=true` and provide a unique `DEMO_PASSWORD`. Demo seeding never creates an administrator.

## Provision an administrator

Start the API once to apply the database migrations, then stop it. From PowerShell, run this one-shot command from `backend`. It reads the database connection from `.env` and the admin identity/password from process environment variables. It does not start the API and does not print the password or hash.

```powershell
$env:ADMIN_EMAIL = Read-Host "Administrator email"
$adminSecret = Read-Host "Administrator password" -AsSecureString
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $adminSecret).Password
mvn -Dexec.mainClass=com.vayomitra.security.AdminProvisioner exec:java
Remove-Item Env:ADMIN_EMAIL, Env:ADMIN_PASSWORD
Remove-Variable adminSecret
```

The password must be at least 12 characters and include letters and numbers. The provisioner refuses an email that already belongs to an account.

## Create local demo users

After migrations have been applied, run this from `backend` to add ten elders and ten volunteers. Pass a different number to create another batch size. The operation is transactional and refuses to proceed if any generated demo email already exists. It prints each generated password once; store the output securely and do not commit it.

```powershell
mvn -Dexec.mainClass=com.vayomitra.security.DemoAccountProvisioner -Dexec.args="10" exec:java
```

These are synthetic test profiles. Elder profiles use a sample Kodakara address; volunteer profiles use a sample Vyttila/Ernakulam address to exercise nearby-request matching. Volunteers receive broad service preferences, weekly availability, and synthetic demo-only verified certificate records so matching can be exercised; they are not evidence for real volunteers and must never be used in production.