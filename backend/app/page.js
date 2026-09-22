export default function Home() {
  return (
    <main style={{ maxWidth: 640 }}>
      <h1 style={{ color: '#2e7d4f' }}>Jadi-Buti API</h1>
      <p>Family medication management backend by ChefoTech. This server only exposes authenticated JSON APIs under <code>/api/v1</code> for the Jadi-Buti Android app.</p>
      <p>Health check: <a href="/api/health">/api/health</a></p>
      <p style={{ fontSize: 14, color: '#555' }}>Jadi-Buti does not diagnose or prescribe medication. Follow your healthcare professional's instructions.</p>
    </main>
  );
}
