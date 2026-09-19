import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const WORK_SECTIONS = [
  "اداری",
  "تولید تیوب نسوز",
  "تولید هد سمپلر",
  "اسمبل",
  "تولید کابل",
  "تولید لنس"
];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, apikey, content-type"
    }
  });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const url = Deno.env.get("SUPABASE_URL") ?? "";
    const anon =
      Deno.env.get("SUPABASE_ANON_KEY") ||
      JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") || "{}").default;
    const service =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ||
      JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}").default;

    if (!url || !anon || !service) return json({ error: "server_configuration_error" }, 500);

    const token = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
    if (!token) return json({ error: "unauthorized" }, 401);

    const caller = createClient(url, anon, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: meData, error: meErr } = await caller.auth.getUser(token);
    if (meErr || !meData.user) return json({ error: "unauthorized" }, 401);

    const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: callerProfile, error: callerProfileErr } = await admin
      .from("profiles").select("role,active").eq("id", meData.user.id).single();

    if (callerProfileErr || !callerProfile?.active || callerProfile.role !== "admin") {
      return json({ error: "forbidden" }, 403);
    }

    const body = await req.json();
    const action = String(body.action || "");

    if (action === "create") {
      const username = String(body.username || "").trim().toLowerCase();
      const password = String(body.password || "");
      const fullName = String(body.full_name || "").trim();
      const rawSections = Array.isArray(body.work_sections)
        ? body.work_sections.map((x: unknown) => String(x || "").trim()).filter(Boolean)
        : [];
      const workSections = [...new Set(rawSections)];
      const department = workSections.length ? workSections.join(" / ") : String(body.department || "").trim();
      const jobTitle = String(body.job_title || "").trim();
      const hireDateRaw = String(body.hire_date || "").trim();
      const hireDate = hireDateRaw || null;
      const role = ["employee", "manager", "admin"].includes(String(body.role)) ? String(body.role) : "employee";

      if (!/^[a-z0-9._-]{3,32}$/.test(username)) return json({ error: "نام کاربری نامعتبر است" }, 400);
      if (password.length < 8) return json({ error: "رمز باید حداقل ۸ کاراکتر باشد" }, 400);
      if (!fullName) return json({ error: "نام پرسنل الزامی است" }, 400);
      if (workSections.length > 2 || workSections.some((x) => !WORK_SECTIONS.includes(x))) {
        return json({ error: "حداکثر دو بخش کاری معتبر انتخاب کنید" }, 400);
      }
      if (hireDate) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(hireDate)) return json({ error: "تاریخ استخدام نامعتبر است" }, 400);
        const d = new Date(hireDate + "T00:00:00Z");
        if (Number.isNaN(d.getTime()) || d.getTime() > Date.now()) {
          return json({ error: "تاریخ استخدام نمی‌تواند در آینده باشد" }, 400);
        }
      }

      const { data: existing } = await admin.from("profiles").select("id").ilike("username", username).maybeSingle();
      if (existing) return json({ error: "این نام کاربری قبلاً استفاده شده است" }, 409);

      const email = username + "@apphr.app";
      const { data: created, error: createErr } = await admin.auth.admin.createUser({
        email, password, email_confirm: true,
        user_metadata: { full_name: fullName, department, job_title: jobTitle }
      });
      if (createErr || !created.user) return json({ error: createErr?.message || "خطا در ساخت کاربر" }, 400);

      const { error: profileErr } = await admin.from("profiles").update({
        username, email, full_name: fullName, department, work_sections: workSections,
        hire_date: hireDate, job_title: jobTitle, role, active: true
      }).eq("id", created.user.id);

      if (profileErr) {
        await admin.auth.admin.deleteUser(created.user.id).catch(() => {});
        return json({ error: profileErr.message || "خطا در ثبت پروفایل" }, 400);
      }
      return json({ ok: true, user_id: created.user.id, username });
    }

    if (action === "reset_password") {
      const userId = String(body.user_id || "");
      const password = String(body.password || "");
      if (!userId || password.length < 8) return json({ error: "ورودی نامعتبر است" }, 400);
      const { error } = await admin.auth.admin.updateUserById(userId, { password });
      if (error) return json({ error: error.message }, 400);
      return json({ ok: true });
    }

    return json({ error: "unknown_action" }, 400);
  } catch (e) {
    return json({ error: String((e as Error)?.message || e || "server_error") }, 500);
  }
});
