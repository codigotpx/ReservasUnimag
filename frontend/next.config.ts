import type { NextConfig } from "next";

// Todo /api/* va al backend de Spring, así el navegador ve un solo origen:
// la cookie httpOnly del JWT viaja sola y no hace falta configurar CORS.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // La raíz lleva al login. Va aquí y no en un page.tsx con redirect(), que no deja validar la navegación instantánea.
  async redirects() {
    return [{ source: "/", destination: "/login", permanent: false }];
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
