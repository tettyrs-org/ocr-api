package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.UUID;

// Master data pegawai (database-schema.md bagian 3; api-spec.md bagian 12). Isi dummy pada V1.0.8.
@Entity
@Table(name = "employees")
public class Employee extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "nip", nullable = false, unique = true, length = 18)
    public String nip;

    @Column(name = "nama", nullable = false)
    public String nama;

    @Column(name = "jabatan")
    public String jabatan;

    @Column(name = "pangkat_golongan")
    public String pangkatGolongan;

    @Column(name = "unit_kerja")
    public String unitKerja;
}
