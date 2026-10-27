import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
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
