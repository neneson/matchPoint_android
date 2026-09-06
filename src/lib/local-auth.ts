import { createServerFn } from "@tanstack/react-start";

/**
 * Autenticación local basada en archivos JSON.
 *
 * Al registrarse se crea un JSON por usuario dentro de una carpeta local oculta
 * (`.matchpoint-auth/usuarios/` en la raíz del proyecto). La contraseña NUNCA se
 * guarda en claro: se cifra con scrypt + salt aleatoria y se almacena como
 * `scrypt:<saltHex>:<hashHex>`.
 *
 * Al iniciar sesión se lee ese mismo JSON y se verifica que el email exista y que
 * la contraseña ingresada, al volver a derivar el hash con la salt guardada,
 * coincida con el hash almacenado (comparación en tiempo constante).
 */

const DIR_OCULTO = ".matchpoint-auth";
const SUBDIR_USUARIOS = "usuarios";

export type UsuarioPublico = {
  nombre: string;
  rut: string;
  email: string;
};

type UsuarioRegistro = UsuarioPublico & {
  password: string; // "scrypt:<saltHex>:<hashHex>"
  creadoEn: string;
};

type ResultadoRegistro =
  | { ok: true; user: UsuarioPublico }
  | { ok: false; error: string };

type ResultadoLogin =
  | { ok: true; user: UsuarioPublico }
  | { ok: false; error: string };

function normalizarEmail(email: string): string {
  return email.trim().toLowerCase();
}

async function rutaArchivoUsuario(email: string) {
  const { createHash } = await import("node:crypto");
  const path = await import("node:path");
  const id = createHash("sha256").update(normalizarEmail(email)).digest("hex");
  const dir = path.join(process.cwd(), DIR_OCULTO, SUBDIR_USUARIOS);
  return { dir, archivo: path.join(dir, `${id}.json`) };
}

async function cifrarPassword(password: string): Promise<string> {
  const { randomBytes, scryptSync } = await import("node:crypto");
  const salt = randomBytes(16);
  const hash = scryptSync(password, salt, 64);
  return `scrypt:${salt.toString("hex")}:${hash.toString("hex")}`;
}

async function passwordCoincide(password: string, cifrada: string): Promise<boolean> {
  const { scryptSync, timingSafeEqual } = await import("node:crypto");
  const [algoritmo, saltHex, hashHex] = cifrada.split(":");
  if (algoritmo !== "scrypt" || !saltHex || !hashHex) return false;
  const salt = Buffer.from(saltHex, "hex");
  const hashGuardado = Buffer.from(hashHex, "hex");
  const hashCandidato = scryptSync(password, salt, hashGuardado.length);
  return (
    hashGuardado.length === hashCandidato.length &&
    timingSafeEqual(hashGuardado, hashCandidato)
  );
}

export const registrarUsuario = createServerFn({ method: "POST" })
  .validator(
    (datos: { nombre: string; rut: string; email: string; password: string }) => datos,
  )
  .handler(async ({ data }): Promise<ResultadoRegistro> => {
    const fs = await import("node:fs/promises");

    const nombre = data.nombre?.trim() ?? "";
    const rut = data.rut?.trim() ?? "";
    const email = normalizarEmail(data.email ?? "");
    const password = data.password ?? "";

    if (!nombre || !rut || !email || !password) {
      return { ok: false, error: "Completa todos los campos." };
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return { ok: false, error: "El email no tiene un formato válido." };
    }
    if (password.length < 6) {
      return { ok: false, error: "La contraseña debe tener al menos 6 caracteres." };
    }

    const { dir, archivo } = await rutaArchivoUsuario(email);

    try {
      await fs.access(archivo);
      return { ok: false, error: "Ese email ya está registrado. Prueba ingresando." };
    } catch {
      // no existe: seguimos
    }

    const registro: UsuarioRegistro = {
      nombre,
      rut,
      email,
      password: await cifrarPassword(password),
      creadoEn: new Date().toISOString(),
    };

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(archivo, JSON.stringify(registro, null, 2), "utf-8");

    return { ok: true, user: { nombre, rut, email } };
  });

export const iniciarSesion = createServerFn({ method: "POST" })
  .validator((datos: { email: string; password: string }) => datos)
  .handler(async ({ data }): Promise<ResultadoLogin> => {
    const fs = await import("node:fs/promises");

    const email = normalizarEmail(data.email ?? "");
    const password = data.password ?? "";

    if (!email || !password) {
      return { ok: false, error: "Ingresa tu email y contraseña." };
    }

    const { archivo } = await rutaArchivoUsuario(email);

    let registro: UsuarioRegistro;
    try {
      registro = JSON.parse(await fs.readFile(archivo, "utf-8")) as UsuarioRegistro;
    } catch {
      return { ok: false, error: "No encontramos una cuenta con ese email." };
    }

    const emailOk = normalizarEmail(registro.email) === email;
    const passwordOk = await passwordCoincide(password, registro.password);

    if (!emailOk || !passwordOk) {
      return { ok: false, error: "Email o contraseña incorrectos." };
    }

    return {
      ok: true,
      user: { nombre: registro.nombre, rut: registro.rut, email: registro.email },
    };
  });
