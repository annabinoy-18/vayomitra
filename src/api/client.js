import { supabase } from "../lib/supabase.js";

const TOKEN_KEY = "vayomitra.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

function genPublicId() {
  const n = Math.floor(Math.random() * 1000000);
  return "VM-" + String(n).padStart(6, "0");
}

export async function loadProfile(userId) {
  const { data, error } = await supabase
    .from("profiles")
    .select("*")
    .eq("id", userId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) return null;

  const profile = { id: data.id, email: data.email, role: data.role, accountStatus: data.account_status };

  if (data.role === "ELDER") {
    const { data: elder } = await supabase
      .from("elders")
      .select("*")
      .eq("user_id", userId)
      .maybeSingle();
    if (elder) {
      Object.assign(profile, {
        fullName: elder.full_name,
        dateOfBirth: elder.date_of_birth,
        gender: elder.gender,
        phone: elder.phone,
        address: elder.address,
        locality: elder.locality,
        city: elder.city,
        state: elder.state,
        country: elder.country,
        pinCode: elder.pin_code,
        latitude: elder.latitude,
        longitude: elder.longitude,
        emergencyContactName: elder.emergency_contact_name,
        emergencyContactPhone: elder.emergency_contact_phone,
      });
    }
  } else if (data.role === "VOLUNTEER") {
    const { data: vol } = await supabase
      .from("volunteers")
      .select("*")
      .eq("user_id", userId)
      .maybeSingle();
    if (vol) {
      const { data: prefs } = await supabase
        .from("volunteer_service_preferences")
        .select("assistance_type")
        .eq("volunteer_id", vol.id);
      Object.assign(profile, {
        volunteerId: vol.id,
        fullName: vol.full_name,
        dateOfBirth: vol.date_of_birth,
        gender: vol.gender,
        phone: vol.phone,
        address: vol.address,
        locality: vol.locality,
        city: vol.city,
        state: vol.state,
        country: vol.country,
        pinCode: vol.pin_code,
        latitude: vol.latitude,
        longitude: vol.longitude,
        educationalQualification: vol.educational_qualification,
        skills: vol.skills,
        experienceYears: vol.experience_years,
        preferredServiceRadiusKm: Number(vol.preferred_service_radius_km),
        offersDriving: vol.offers_driving,
        drivingLicenceNumber: vol.driving_licence_number,
        drivingLicenceExpiry: vol.driving_licence_expiry,
        assistanceTypes: (prefs || []).map((p) => p.assistance_type),
      });
    }
  }
  return profile;
}

export const AssistanceApi = {
  async types() {
    const { data, error } = await supabase
      .from("assistance_types")
      .select("*")
      .order("display_name");
    if (error) throw new Error(error.message);
    return data.map((t) => ({
      code: t.code,
      displayName: t.display_name,
      description: t.description,
      requiresDrivingLicence: t.requires_driving_licence,
      requiresHealthcareCertificate: t.requires_healthcare_certificate,
      requiresFirstAidCertificate: t.requires_first_aid_certificate,
    }));
  },
};

export async function registerElder(form) {
  const { data: authData, error: authError } = await supabase.auth.signUp({
    email: form.email,
    password: form.password,
  });
  if (authError) throw new Error(authError.message);
  const userId = authData.user.id;

  const { error: profileError } = await supabase.from("profiles").insert({
    id: userId,
    email: form.email,
    role: "ELDER",
  });
  if (profileError) throw new Error(profileError.message);

  const { error: elderError } = await supabase.from("elders").insert({
    user_id: userId,
    full_name: form.fullName,
    date_of_birth: form.dateOfBirth,
    gender: form.gender,
    phone: form.phone,
    address: form.address,
    locality: form.locality,
    city: form.city,
    state: form.state,
    country: form.country,
    pin_code: form.pinCode,
    latitude: form.latitude || null,
    longitude: form.longitude || null,
    emergency_contact_name: form.emergencyContactName,
    emergency_contact_phone: form.emergencyContactPhone,
  });
  if (elderError) throw new Error(elderError.message);
}

export async function registerVolunteer(form) {
  const { data: authData, error: authError } = await supabase.auth.signUp({
    email: form.email,
    password: form.password,
  });
  if (authError) throw new Error(authError.message);
  const userId = authData.user.id;

  const { error: profileError } = await supabase.from("profiles").insert({
    id: userId,
    email: form.email,
    role: "VOLUNTEER",
  });
  if (profileError) throw new Error(profileError.message);

  const { data: volData, error: volError } = await supabase
    .from("volunteers")
    .insert({
      user_id: userId,
      full_name: form.fullName,
      date_of_birth: form.dateOfBirth,
      gender: form.gender,
      phone: form.phone,
      address: form.address,
      locality: form.locality,
      city: form.city,
      state: form.state,
      country: form.country,
      pin_code: form.pinCode,
      latitude: form.latitude || null,
      longitude: form.longitude || null,
      educational_qualification: form.educationalQualification,
      skills: form.skills,
      experience_years: parseInt(form.experienceYears, 10) || 0,
      preferred_service_radius_km: parseFloat(form.preferredServiceRadiusKm) || 15,
      offers_driving: form.offersDriving,
      driving_licence_number: form.offersDriving ? form.drivingLicenceNumber : null,
      driving_licence_expiry: form.offersDriving ? form.drivingLicenceExpiry : null,
    })
    .select()
    .single();
  if (volError) throw new Error(volError.message);

  if (form.assistanceTypes && form.assistanceTypes.length > 0) {
    const rows = form.assistanceTypes.map((code) => ({
      volunteer_id: volData.id,
      assistance_type: code,
    }));
    const { error: prefError } = await supabase
      .from("volunteer_service_preferences")
      .insert(rows);
    if (prefError) throw new Error(prefError.message);
  }
}

export async function listElderRequests() {
  const { data: u } = await supabase.auth.getUser();
  const { data: elder } = await supabase
    .from("elders")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!elder) return [];

  const { data: requests, error } = await supabase
    .from("assistance_requests")
    .select("*")
    .eq("elder_id", elder.id)
    .order("created_at", { ascending: false });
  if (error) throw new Error(error.message);

  const enriched = await Promise.all(
    requests.map(async (r) => {
      const { count } = await supabase
        .from("match_recommendations")
        .select("*", { count: "exact", head: true })
        .eq("request_id", r.id);
      const { data: asg } = await supabase
        .from("volunteer_assignments")
        .select("status, volunteer_id")
        .eq("request_id", r.id)
        .maybeSingle();
      return {
        id: r.id,
        publicId: r.public_id,
        assistanceType: r.assistance_type,
        title: r.title,
        description: r.description,
        preferredDate: r.preferred_date,
        preferredTimeStart: r.preferred_time_start,
        preferredTimeEnd: r.preferred_time_end,
        serviceAddress: r.service_address,
        city: r.city,
        status: r.status,
        requiresDriving: r.requires_driving,
        eligibleVolunteerCount: count || 0,
        assignmentStatus: asg?.status,
      };
    })
  );
  return enriched;
}

export async function getRequest(requestId, userRole) {
  const { data: r, error } = await supabase
    .from("assistance_requests")
    .select("*")
    .eq("id", requestId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!r) throw new Error("Request not found.");

  const request = {
    id: r.id,
    publicId: r.public_id,
    assistanceType: r.assistance_type,
    title: r.title,
    description: r.description,
    preferredDate: r.preferred_date,
    preferredTimeStart: r.preferred_time_start,
    preferredTimeEnd: r.preferred_time_end,
    serviceAddress: r.service_address,
    locality: r.locality,
    city: r.city,
    state: r.state,
    pinCode: r.pin_code,
    latitude: r.latitude,
    longitude: r.longitude,
    priority: r.priority,
    requiresDriving: r.requires_driving,
    additionalDetails: r.additional_details,
    status: r.status,
  };

  const { data: asg } = await supabase
    .from("volunteer_assignments")
    .select("id, status, volunteer_id, accepted_at, started_at, completed_at")
    .eq("request_id", requestId)
    .maybeSingle();

  if (asg) {
    request.assignment = {
      status: asg.status,
      volunteerName: asg.volunteer_name || "Unknown",
      volunteerPhone: asg.volunteer_phone,
      volunteerCity: null,
    };
  } else {
    request.assignment = null;
  }

  if (userRole === "ELDER" || userRole === "ADMIN") {
    const { data: matches } = await supabase
      .from("match_recommendations")
      .select("*")
      .eq("request_id", requestId)
      .order("rank_position", { ascending: true });

    request.matches = (matches || []).map((m) => ({
      volunteerId: m.volunteer_id,
      fullName: m.volunteer_name || "Unknown",
      phone: m.volunteer_phone,
      email: m.volunteer_email,
      city: m.volunteer_city,
      experienceYears: m.volunteer_experience_years || 0,
      educationalQualification: m.volunteer_educational_qualification || "",
      skills: m.volunteer_skills || "",
      totalScore: Number(m.total_score),
      skillScore: Number(m.skill_score),
      distanceScore: Number(m.distance_score),
      availabilityScore: Number(m.availability_score),
      qualificationScore: Number(m.qualification_score),
      distanceKm: m.distance_km != null ? Number(m.distance_km) : null,
      explanation: m.explanation,
      missingInformation: m.missing_information,
      rank: m.rank_position,
    }));
    request.matchSummary = { eligibleCount: request.matches.length, candidateCount: request.matches.length };
  }

  return request;
}

export async function createRequest(form, elderProfile) {
  const { data: elder } = await supabase
    .from("elders")
    .select("id")
    .eq("user_id", elderProfile.id)
    .maybeSingle();
  if (!elder) throw new Error("Elder profile not found.");

  const requiresDriving = form.requiresDriving || form.assistanceType === "TRANSPORTATION";
  const publicId = genPublicId();

  const { data: req, error } = await supabase
    .from("assistance_requests")
    .insert({
      public_id: publicId,
      elder_id: elder.id,
      assistance_type: form.assistanceType,
      title: form.title,
      description: form.description,
      preferred_date: form.preferredDate,
      preferred_time_start: form.preferredTimeStart,
      preferred_time_end: form.preferredTimeEnd,
      service_address: form.serviceAddress,
      locality: form.locality || null,
      city: form.city || null,
      state: form.state || null,
      pin_code: form.pinCode || null,
      latitude: form.latitude || null,
      longitude: form.longitude || null,
      priority: form.priority,
      requires_driving: requiresDriving,
      additional_details: form.additionalDetails || null,
    })
    .select()
    .single();
  if (error) throw new Error(error.message);

  await insertHistory(req.id, null, "PENDING", "Request created");
  const { error: rpcError } = await supabase.rpc("compute_matches", { p_request_id: req.id });
  if (rpcError) throw new Error(rpcError.message);
  return { id: req.id };
}

async function rebuildMatches(request) {
  const { error } = await supabase.rpc("compute_matches", { p_request_id: request.id });
  if (error) throw new Error(error.message);
}

export async function refreshMatches(requestId) {
  const { error } = await supabase.rpc("compute_matches", { p_request_id: requestId });
  if (error) throw new Error(error.message);
}

export async function listVolunteerAvailability() {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) return [];
  const { data, error } = await supabase
    .from("volunteer_availability")
    .select("id, weekday, start_time, end_time")
    .eq("volunteer_id", vol.id)
    .order("weekday", { ascending: true });
  if (error) throw new Error(error.message);
  return (data || []).map((a) => ({ id: a.id, weekday: a.weekday, startTime: a.start_time, endTime: a.end_time }));
}

export async function addVolunteerAvailability(weekday, startTime, endTime) {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) throw new Error("Volunteer profile not found.");
  const { error } = await supabase.from("volunteer_availability").insert({
    volunteer_id: vol.id, weekday, start_time: startTime, end_time: endTime,
  });
  if (error) throw new Error(error.message);
}

export async function removeVolunteerAvailability(id) {
  const { error } = await supabase.from("volunteer_availability").delete().eq("id", id);
  if (error) throw new Error(error.message);
}

async function insertHistory(requestId, oldStatus, newStatus, note) {
  const { data: u } = await supabase.auth.getUser();
  await supabase.from("request_status_history").insert({
    request_id: requestId,
    old_status: oldStatus,
    new_status: newStatus,
    changed_by: u.user?.id,
    note,
  });
}

export async function assignVolunteer(requestId, volunteerId) {
  const { data: req } = await supabase
    .from("assistance_requests")
    .select("status")
    .eq("id", requestId)
    .maybeSingle();
  if (!req || !["PENDING", "MATCHED"].includes(req.status))
    throw new Error("This request is no longer open for assignment.");

  const { error } = await supabase.rpc("assign_volunteer", {
    p_request_id: requestId,
    p_volunteer_id: volunteerId,
  });
  if (error) {
    if (error.code === "23505") throw new Error("This request already has a volunteer assigned.");
    throw new Error(error.message);
  }
}

export async function applyToRequest(requestId, volunteerId) {
  return assignVolunteer(requestId, volunteerId);
}

export async function transitionAssignment(requestId, action) {
  const { data: asg } = await supabase
    .from("volunteer_assignments")
    .select("id, status, volunteer_id")
    .eq("request_id", requestId)
    .maybeSingle();
  if (!asg) throw new Error("No assignment found for this request.");

  const { data: req } = await supabase
    .from("assistance_requests")
    .select("status")
    .eq("id", requestId)
    .maybeSingle();

  const transitions = {
    accept: { from: "ASSIGNED", toReq: "ACCEPTED", toAsg: "ACCEPTED", note: "Volunteer accepted" },
    start: { from: "ACCEPTED", toReq: "IN_PROGRESS", toAsg: "IN_PROGRESS", note: "Volunteer started" },
    complete: { from: "IN_PROGRESS", toReq: "COMPLETED", toAsg: "COMPLETED", note: "Volunteer completed" },
  };
  const t = transitions[action];
  if (!t) throw new Error("Unsupported action.");
  if (asg.status !== t.from) throw new Error(`This action is not allowed from the current status (${asg.status}).`);

  const update = { status: t.toAsg };
  if (action === "accept") update.accepted_at = new Date().toISOString();
  if (action === "start") update.started_at = new Date().toISOString();
  if (action === "complete") update.completed_at = new Date().toISOString();

  await supabase.from("volunteer_assignments").update(update).eq("id", asg.id);
  await supabase.from("assistance_requests").update({ status: t.toReq }).eq("id", requestId);
  await insertHistory(requestId, req.status, t.toReq, t.note);
}

export async function cancelRequest(requestId) {
  const { data: req } = await supabase
    .from("assistance_requests")
    .select("status")
    .eq("id", requestId)
    .maybeSingle();
  if (!req) throw new Error("Request not found.");
  if (!["PENDING", "MATCHED", "ASSIGNED", "ACCEPTED"].includes(req.status))
    throw new Error("This request can no longer be cancelled.");

  await supabase.from("assistance_requests").update({ status: "CANCELLED" }).eq("id", requestId);
  await supabase
    .from("volunteer_assignments")
    .update({ status: "DECLINED" })
    .eq("request_id", requestId)
    .in("status", ["ASSIGNED", "ACCEPTED"]);
  await insertHistory(requestId, req.status, "CANCELLED", "Cancelled by elder");
}

export async function listVolunteerAvailableRequests() {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) throw new Error("Volunteer profile not found.");

  const { data: matches } = await supabase
    .from("match_recommendations")
    .select("request_id, total_score")
    .eq("volunteer_id", vol.id);

  if (!matches || matches.length === 0) return [];

  const requestIds = matches.map((m) => m.request_id);
  const { data: requests } = await supabase
    .from("assistance_requests")
    .select("*")
    .in("id", requestIds)
    .in("status", ["PENDING", "MATCHED"])
    .order("created_at", { ascending: false });

  const scoreMap = {};
  matches.forEach((m) => { scoreMap[m.request_id] = Number(m.total_score); });

  return (requests || []).map((r) => ({
    id: r.id,
    title: r.title,
    description: r.description,
    assistanceType: r.assistance_type,
    preferredDate: r.preferred_date,
    requiresDriving: r.requires_driving,
    status: r.status,
    matchScore: scoreMap[r.id] || null,
    serviceAddress: r.service_address,
  }));
}

export async function listVolunteerAssignments() {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) return [];

  const { data: assignments } = await supabase
    .from("volunteer_assignments")
    .select("id, status, request_id")
    .eq("volunteer_id", vol.id)
    .neq("status", "DECLINED")
    .order("assigned_at", { ascending: false });

  if (!assignments || assignments.length === 0) return [];

  const requestIds = assignments.map((a) => a.request_id);
  const { data: requests } = await supabase
    .from("assistance_requests")
    .select("*")
    .in("id", requestIds);

  const reqMap = {};
  (requests || []).forEach((r) => { reqMap[r.id] = r; });

  return assignments.map((a) => {
    const r = reqMap[a.request_id];
    if (!r) return null;
    return {
      id: r.id,
      title: r.title,
      assistanceType: r.assistance_type,
      preferredDate: r.preferred_date,
      serviceAddress: r.service_address,
      status: a.status,
    };
  }).filter(Boolean);
}

export async function getVolunteerReadiness() {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id, offers_driving, driving_licence_expiry")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) throw new Error("Volunteer profile not found.");

  const { data: certs } = await supabase
    .from("volunteer_certificates")
    .select("certificate_type, verification_status")
    .eq("volunteer_id", vol.id);
  const verified = new Set((certs || []).filter((c) => c.verification_status === "VERIFIED").map((c) => c.certificate_type));

  const { data: prefs } = await supabase
    .from("volunteer_service_preferences")
    .select("assistance_type")
    .eq("volunteer_id", vol.id);

  const expiry = vol.driving_licence_expiry ? new Date(vol.driving_licence_expiry) : null;
  return {
    tenthCertificateVerified: verified.has("TENTH_STANDARD"),
    qualificationCertificateVerified: verified.has("QUALIFICATION"),
    mandatoryCertificatesVerified: verified.has("TENTH_STANDARD") && verified.has("QUALIFICATION"),
    offersDriving: vol.offers_driving,
    drivingLicenceVerified: verified.has("DRIVING_LICENCE"),
    drivingLicenceExpired: expiry ? expiry < new Date() : false,
    healthcareCertificateVerified: verified.has("HEALTHCARE"),
    firstAidCertificateVerified: verified.has("FIRST_AID"),
    assistanceTypes: (prefs || []).map((p) => p.assistance_type),
  };
}

export async function listVolunteerDocuments() {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) return [];

  const { data: docs } = await supabase
    .from("volunteer_documents")
    .select("*")
    .eq("volunteer_id", vol.id)
    .order("created_at", { ascending: false });

  return (docs || []).map((d) => ({
    documentId: d.id,
    documentType: d.document_type,
    originalFilename: d.original_filename,
    verificationStatus: d.verification_status,
  }));
}

export async function listElderDocuments() {
  const { data: u } = await supabase.auth.getUser();
  const { data: elder } = await supabase
    .from("elders")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!elder) return [];

  const { data: docs } = await supabase
    .from("elder_documents")
    .select("*")
    .eq("elder_id", elder.id)
    .order("created_at", { ascending: false });

  return (docs || []).map((d) => ({
    documentId: d.id,
    documentType: d.document_type,
    originalFilename: d.original_filename,
    verificationStatus: d.verification_status,
  }));
}

export async function uploadVolunteerDocument(documentType, file) {
  const { data: u } = await supabase.auth.getUser();
  const { data: vol } = await supabase
    .from("volunteers")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!vol) throw new Error("Volunteer profile not found.");

  const docId = crypto.randomUUID();
  const ext = file.name.split(".").pop();
  const path = `volunteers/${vol.id}/${docId}.${ext}`;

  const { error: uploadError } = await supabase.storage
    .from("documents")
    .upload(path, file);
  if (uploadError) throw new Error(uploadError.message);

  const certTypeMap = {
    TENTH_CERTIFICATE: "TENTH_STANDARD",
    QUALIFICATION_CERTIFICATE: "QUALIFICATION",
    DRIVING_LICENCE: "DRIVING_LICENCE",
    HEALTHCARE_CERTIFICATE: "HEALTHCARE",
    FIRST_AID_CERTIFICATE: "FIRST_AID",
    OTHER: "OTHER",
  };

  const { error: docError } = await supabase.from("volunteer_documents").insert({
    id: docId,
    volunteer_id: vol.id,
    document_type: documentType,
    original_filename: file.name,
    storage_path: path,
    content_type: file.type,
    file_size_bytes: file.size,
  });
  if (docError) throw new Error(docError.message);

  const { error: certError } = await supabase.from("volunteer_certificates").insert({
    volunteer_id: vol.id,
    document_id: docId,
    certificate_type: certTypeMap[documentType] || "OTHER",
    title: file.name,
  });
  if (certError) throw new Error(certError.message);
}

export async function uploadElderDocument(documentType, file) {
  const { data: u } = await supabase.auth.getUser();
  const { data: elder } = await supabase
    .from("elders")
    .select("id")
    .eq("user_id", u.user.id)
    .maybeSingle();
  if (!elder) throw new Error("Elder profile not found.");

  const docId = crypto.randomUUID();
  const ext = file.name.split(".").pop();
  const path = `elders/${elder.id}/${docId}.${ext}`;

  const { error: uploadError } = await supabase.storage
    .from("documents")
    .upload(path, file);
  if (uploadError) throw new Error(uploadError.message);

  const { error: docError } = await supabase.from("elder_documents").insert({
    id: docId,
    elder_id: elder.id,
    document_type: documentType,
    original_filename: file.name,
    storage_path: path,
    content_type: file.type,
    file_size_bytes: file.size,
  });
  if (docError) throw new Error(docError.message);
}

export async function listAdminDocuments() {
  const { data: vDocs } = await supabase
    .from("volunteer_documents")
    .select("id, document_type, original_filename, verification_status, created_at, volunteer_id")
    .eq("verification_status", "PENDING")
    .order("created_at", { ascending: true });

  const { data: eDocs } = await supabase
    .from("elder_documents")
    .select("id, document_type, original_filename, verification_status, created_at, elder_id")
    .eq("verification_status", "PENDING")
    .order("created_at", { ascending: true });

  const docs = [];

  for (const d of vDocs || []) {
    const { data: vol } = await supabase
      .from("volunteers")
      .select("full_name, user_id")
      .eq("id", d.volunteer_id)
      .maybeSingle();
    const { data: prof } = await supabase
      .from("profiles")
      .select("email")
      .eq("id", vol?.user_id)
      .maybeSingle();
    docs.push({
      documentId: d.id,
      ownerType: "VOLUNTEER",
      documentType: d.document_type,
      originalFilename: d.original_filename,
      verificationStatus: d.verification_status,
      volunteerName: vol?.full_name || "Unknown",
      volunteerEmail: prof?.email || "",
    });
  }

  for (const d of eDocs || []) {
    const { data: elder } = await supabase
      .from("elders")
      .select("full_name, user_id")
      .eq("id", d.elder_id)
      .maybeSingle();
    const { data: prof } = await supabase
      .from("profiles")
      .select("email")
      .eq("id", elder?.user_id)
      .maybeSingle();
    docs.push({
      documentId: d.id,
      ownerType: "ELDER",
      documentType: d.document_type,
      originalFilename: d.original_filename,
      verificationStatus: d.verification_status,
      volunteerName: elder?.full_name || "Unknown",
      volunteerEmail: prof?.email || "",
    });
  }

  return { documents: docs };
}

export async function reviewDocument(documentId, status, ownerType) {
  const table = ownerType === "ELDER" ? "elder_documents" : "volunteer_documents";
  const { error } = await supabase
    .from(table)
    .update({ verification_status: status, reviewer_notes: "Reviewed by an administrator." })
    .eq("id", documentId);
  if (error) throw new Error(error.message);

  if (ownerType !== "ELDER") {
    await supabase
      .from("volunteer_certificates")
      .update({ verification_status: status })
      .eq("document_id", documentId);
  }
}

export async function openDocumentFile(documentId, ownerType) {
  const table = ownerType === "ELDER" ? "elder_documents" : "volunteer_documents";
  const { data: doc } = await supabase
    .from(table)
    .select("storage_path")
    .eq("id", documentId)
    .maybeSingle();
  if (!doc) throw new Error("Document not found.");

  const { data, error } = await supabase.storage
    .from("documents")
    .createSignedUrl(doc.storage_path, 60);
  if (error) throw new Error(error.message);

  window.open(data.signedUrl, "_blank");
}
