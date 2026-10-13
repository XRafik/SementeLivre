import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
  // As fotos do catálogo vêm do backend como caminho relativo ("/uploads/produtos/x.png")
  async rewrites() {
    return [{ source: "/uploads/:path*", destination: `${BACKEND_URL}/uploads/:path*` }];
  },
};

export default nextConfig;
