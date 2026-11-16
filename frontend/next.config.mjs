/** @type {import('next').NextConfig} */
// Same-origin proxy: browsers send both hl_auth and XSRF-TOKEN to the frontend host.
// Next.js forwards cookies and CSRF headers to Spring Boot without exposing JWTs to JS.
const backend = (process.env.API_PROXY_TARGET || "http://127.0.0.1:8081").replace(/\/$/, "");
const nextConfig = {
  devIndicators: false, // Hide the Next.js dev badge in the bottom-left corner.
  async rewrites() {
    return [{ source: "/backend/:path*", destination: `${backend}/:path*` }];
  },
};
export default nextConfig;
