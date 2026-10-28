import type { NextConfig } from "next";

const API_ORIGIN = new URL(process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080').origin;
const DEV = process.env.NODE_ENV !== 'production';

// Cabeçalhos de segurança (#202). O navegador fala com a API (NEXT_PUBLIC_API_URL), de
// onde também vêm as fotos, e com o ViaCEP; as fontes vêm do Google Fonts.
const CSP = [
  "default-src 'self'",
  // 'unsafe-inline': o Next põe scripts inline na hidratação; 'unsafe-eval' só no next dev
  `script-src 'self' 'unsafe-inline'${DEV ? " 'unsafe-eval'" : ''}`,
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' data: https://fonts.gstatic.com",
  `img-src 'self' data: blob: ${API_ORIGIN} https://firebasestorage.googleapis.com`,
  `connect-src 'self' ${API_ORIGIN} https://viacep.com.br${DEV ? ' ws: wss:' : ''}`,
  "worker-src 'self' blob:",
  "manifest-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

const CABECALHOS_DE_SEGURANCA = [
  { key: 'Content-Security-Policy', value: CSP },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  // Só vale em HTTPS; o navegador ignora em http://localhost
  { key: 'Strict-Transport-Security', value: 'max-age=31536000' },
  // A foto do produto usa <input capture>, que abre a câmera do sistema sem pedir permissão à página
  { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(), payment=(), usb=()' },
];

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
  // Sem o "X-Powered-By: Next.js", que só conta a quem ataca qual é a stack
  poweredByHeader: false,
  async headers() {
    return [{ source: '/:path*', headers: CABECALHOS_DE_SEGURANCA }];
  },
  // As telas de produto eram /sementes (#186): links e favoritos antigos continuam valendo
  async redirects() {
    return [
      { source: '/sementes', destination: '/produtos', permanent: true },
      { source: '/sementes/:path*', destination: '/produtos/:path*', permanent: true },
    ];
  },
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'firebasestorage.googleapis.com',
      },
    ],
  },
};

export default nextConfig;
