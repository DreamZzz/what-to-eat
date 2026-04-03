const http = require('http');
const https = require('https');

// Simulator-only flows keep the proxy on loopback, while physical-device local
// mode binds on 0.0.0.0 so the phone can reach the Mac over the same LAN.
const bindHost = process.env.DEV_PROXY_BIND_HOST || '127.0.0.1';
const targetProtocol = process.env.DEV_PROXY_TARGET_PROTOCOL || 'http';
const targetHost = process.env.DEV_PROXY_TARGET_HOST || 'api.example.com';
const targetPort = Number(process.env.DEV_PROXY_TARGET_PORT || 80);
const localPort = Number(process.env.DEV_PROXY_LOCAL_PORT || 18080);
const transport = targetProtocol === 'https' ? https : http;

const server = http.createServer((req, res) => {
  const proxyReq = transport.request(
    {
      protocol: `${targetProtocol}:`,
      hostname: targetHost,
      port: targetPort,
      path: req.url,
      method: req.method,
      headers: {
        ...req.headers,
        host: targetHost,
      },
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
      proxyRes.pipe(res);
    }
  );

  proxyReq.on('error', (error) => {
    console.error(`[dev-proxy] ${req.method} ${req.url} -> ${error.message}`);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    }
    res.end(
      JSON.stringify({
        error: 'proxy_error',
        message: error.message,
        target: `http://${targetHost}:${targetPort}`,
      })
    );
  });

  req.pipe(proxyReq);
});

server.listen(localPort, bindHost, () => {
  console.log(
    `[dev-proxy] listening on http://${bindHost}:${localPort} -> ${targetProtocol}://${targetHost}:${targetPort}`
  );
});
