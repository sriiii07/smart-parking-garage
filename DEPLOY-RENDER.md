# Host the Java parking demo on Render

This is a **public academic simulation**, not a real parking/payment system. The server has no login, and anybody with the URL can create or exit demo sessions. Use only fictitious vehicle registrations. Tickets and history are held **in memory** and reset whenever the service sleeps, restarts, or redeploys. Render documents its Free web service idle spin-down behavior [6](https://render.com/docs/free).

## 1. Add the deployment files to your local Git repository

Copy `Dockerfile`, `.dockerignore`, and `DEPLOY-RENDER.md` into the **same folder as `pom.xml`**, e.g. `C:\Users\HP\Desktop\JavaProject`. Do not place them in `src/` or inside a second nested project folder.

In the VS Code PowerShell terminal in that folder:

```powershell
dir Dockerfile, pom.xml
git add Dockerfile .dockerignore DEPLOY-RENDER.md
git commit -m "Add Docker deployment for Java parking demo"
git push
```

Check your repository page to ensure `Dockerfile` appears at the repository root. If you prefer to test with local Docker first (optional):

```powershell
docker build -t smart-parking-demo .
docker run --rm -p 8080:10000 smart-parking-demo
```

Then open `http://localhost:8080/`. Local Docker is **not required** to deploy with Render; Render can build from the Dockerfile in your repository [7](https://render.com/docs/docker).

## 2. Create a Render web service

1. Sign in at [render.com](https://render.com/).
2. Dashboard → **New** → **Web Service**. **Connect your GitHub account as a Git provider and select the repository from the connected-repository list. Do not use the Public Git Repository URL option if you need automatic redeployment:** Render requires a connected Git provider for auto-deploys [1](https://render.com/docs/deploys).
3. Select the repository containing `pom.xml` and `Dockerfile`, and choose branch `main`.
4. Choose **Docker** as the language/runtime (not Static Site). Keep **Root Directory** empty if `pom.xml` is at the repository root; use the repository-root `Dockerfile`. Render documents Docker-based web services [7](https://render.com/docs/docker).
5. If the **Free** instance type is offered, select it to avoid selecting a paid plan inadvertently; review the plan shown before clicking Create. Render documents Free web-service limitations [6](https://render.com/docs/free).
6. Optional health check path: `/api/state`. No database or secret environment variables are required for this **in-memory** demo. The Dockerfile sets a fallback `PORT=10000`; the Java server reads `PORT` and binds to `0.0.0.0`, matching Render's port guidance [1](https://render.com/docs/web-services).
7. Click **Create Web Service** and wait for the build/deploy to succeed. Open the URL shown in Render's dashboard; `YOUR_RENDER_URL/api/state` should return JSON with `capacity: 10`.

In Render, keep **Auto-Deploy: On Commit** for branch `main`. When you change source code locally, commit and push to `main`; Render will rebuild and deploy the new commit automatically. Merely saving files in VS Code does not update the hosted site [1](https://render.com/docs/deploys).

## Troubleshooting

- **Dockerfile not found:** ensure it is visible at the repository root online, not only on your computer. Leave the Render Root Directory empty when the project root is the repository root.
- **Wrong service type:** use **Web Service / Docker**, not GitHub Pages or a static site. This app requires a running Java HTTP server.
- **Port or health check failure:** check Render logs for `Northstar Smart Parking running on http://0.0.0.0:<PORT>`. The server already reads the `PORT` environment variable.
- **First visit is slow or records disappear:** Free services spin down when idle; this app has no persistent database [6](https://render.com/docs/free). MySQL/JDBC is a separate unfinished project phase.
- **Unexpected charges:** verify you selected the intended plan before creating the service. Do not add paid storage or databases for this simple demo.

## Future work

If you need records to survive restarts, implement the MySQL/JDBC persistence phase first and host the database separately. Do not claim durable parking history from the current version.
