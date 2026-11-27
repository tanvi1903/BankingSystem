const { spawn } = require('child_process');
const path = require('path');

console.log('\x1b[36mStarting Spring Boot Backend (8081) & React Frontend (5173)...\x1b[0m');

const backend = spawn('cmd.exe', ['/c', 'mvnw.cmd spring-boot:run'], {
  cwd: __dirname,
  stdio: 'inherit'
});

const frontend = spawn('cmd.exe', ['/c', 'npm run dev'], {
  cwd: path.join(__dirname, 'frontend'),
  stdio: 'inherit'
});

process.on('SIGINT', () => {
  backend.kill();
  frontend.kill();
  process.exit();
});