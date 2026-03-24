import { createContext, useContext, useState, useEffect } from "react";

type Theme = "dark" | "light";
interface ThemeCtx { theme: Theme; toggle: () => void; isDark: boolean; }
const ThemeContext = createContext<ThemeCtx>({} as ThemeCtx);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() =>
    (localStorage.getItem("sentinel_theme") as Theme) || "dark");

  useEffect(() => {
    localStorage.setItem("sentinel_theme", theme);
    document.body.setAttribute("data-theme", theme);
  }, [theme]);

  const toggle = () => setTheme(t => t === "dark" ? "light" : "dark");
  return (
    <ThemeContext.Provider value={{ theme, toggle, isDark: theme === "dark" }}>
      {children}
    </ThemeContext.Provider>
  );
}
export const useTheme = () => useContext(ThemeContext);