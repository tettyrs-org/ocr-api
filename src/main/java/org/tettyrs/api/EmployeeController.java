package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.entities.Employee;
import org.tettyrs.service.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Pencarian master pegawai untuk admin (api-spec.md bagian 12). Hanya ADMIN (AuthorizationFilter).
@Path("/api/v1/admin/employees")
@Produces(MediaType.APPLICATION_JSON)
public class EmployeeController {

    @Inject
    ProfileService profileService;

    @GET
    public Response search(@QueryParam("q") String q) {
        List<Map<String, Object>> data = profileService.search(q).stream()
                .map(e -> Map.<String, Object>of(
                        "nip", e.nip,
                        "nama", e.nama,
                        "jabatan", e.jabatan == null ? "" : e.jabatan,
                        "pangkat_golongan", e.pangkatGolongan == null ? "" : e.pangkatGolongan,
                        "unit_kerja", e.unitKerja == null ? "" : e.unitKerja))
                .collect(Collectors.toList());
        return Response.ok(ApiResponse.success(data)).build();
    }
}
