/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  serverExternalPackages: ['mongoose', '@anthropic-ai/sdk'],
};
export default nextConfig;
