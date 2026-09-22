export const metadata = { title: 'Jadi-Buti API', description: 'Jadi-Buti family medication management backend by ChefoTech' };

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: 'system-ui, sans-serif', margin: 0, padding: 32, background: '#f4f8f4', color: '#1d2a1f' }}>{children}</body>
    </html>
  );
}
