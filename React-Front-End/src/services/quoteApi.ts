const API = "http://localhost:8080";

//retrieves customer from server-side session
export async function getCurrentSession(): Promise<{
  display_name: string;
}> {
  const response = await fetch(`${API}/api/session`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Could not load customer session");
  }

  return response.json();
}

// Retrieves customers active quote 
export async function getQuoteConfig(): Promise<{
  versionCode: string;
  description: string;
  currency: string;
  config: Record<string, unknown>;
}> {
  const response = await fetch(`${API}/api/quote-config`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Could not load quote configuration");
  }

  return response.json();
}
