import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const DEV = process.env.NODE_ENV !== "production";

// Cabeçalhos de segurança (#202). O site só fala com a própria origem: os dados vêm das
// rotas /api dele, as fotos do /uploads (rewrite abaixo) e a fonte do next/font.
const CSP = [
  "default-src 'self'",
  // 'unsafe-inline': o Next põe scripts inline na hidratação; 'unsafe-eval' só no next dev
  `script-src 'self' 'unsafe-inline'${DEV ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "font-src 'self' data:",
  "img-src 'self' data: blob:",
  `connect-src 'self'${DEV ? " ws: wss:" : ""}`,
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const CABECALHOS_DE_SEGURANCA = [
  { key: "Content-Security-Policy", value: CSP },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // Só vale em HTTPS; o navegador ignora em http://localhost
  { key: "Strict-Transport-Security", value: "max-age=31536000" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()" },
];

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
  // Sem o "X-Powered-By: Next.js", que só conta a quem ataca qual é a stack
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: CABECALHOS_DE_SEGURANCA }];
  },
  // As fotos do catálogo vêm do backend como caminho relativo ("/uploads/produtos/x.png")
  async rewrites() {
    return [{ source: "/uploads/:path*", destination: `${BACKEND_URL}/uploads/:path*` }];
  },
};

export default nextConfig;
