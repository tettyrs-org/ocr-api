-- Master pegawai (api-spec bagian 12). DATA DUMMY: nama dan NIP fiktif. Pola NIP hanya meniru format asli.
-- Ganti dengan data kepegawaian asli (OQ-3) sebelum produksi, lalu hapus baris dummy ini.
CREATE TABLE IF NOT EXISTS employees (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nip               VARCHAR(18) NOT NULL UNIQUE,
    nama              TEXT NOT NULL,
    jabatan           TEXT,
    pangkat_golongan  TEXT,
    unit_kerja        TEXT,
    external_ref      TEXT,
    synced_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_employees_nama ON employees (LOWER(nama));

INSERT INTO employees (nip, nama, jabatan, pangkat_golongan) VALUES
('198503122010011004', 'Budi Santoso', 'Analis Perbendaharaan', 'Penata Muda Tingkat I (III/b)'),
('199007152015022003', 'Siti Rahayu', 'Pelaksana Verifikasi', 'Penata (III/c)'),
('198612032011012006', 'Rina Lestari', 'Bendahara Pengeluaran', 'Penata Muda (III/a)'),
('198004252005011008', 'Dedi Kurniawan', 'Staf Akuntansi', 'Pengatur (II/c)'),
('198209202008011005', 'Agus Wijaya', 'Operator Data', 'Pengatur Tingkat I (II/d)'),
('199402112018022004', 'Maya Sari', 'Analis Anggaran', 'Penata Muda (III/a)'),
('199111052014031007', 'Yoga Pratama', 'Teknisi Komputer', 'Pengatur (II/c)'),
('198709171209011001', 'Andi Pratama', 'Analis Perbendaharaan', 'Pengatur (II/c)'),
('19980109609131002', 'Dewi Utami', 'Pelaksana Verifikasi', 'Pengatur Tingkat I (II/d)'),
('19750525501251003', 'Rudi Siregar', 'Pengadministrasi Umum', 'Penata Muda (III/a)'),
('199109051105211004', 'Sri Wijaya', 'Bendahara Pembantu', 'Penata Muda Tingkat I (III/b)'),
('19840117701131005', 'Hendra Ramadhan', 'Staf Akuntansi', 'Penata (III/c)'),
('197909251005091006', 'Lestari Hidayat', 'Pengelola Data', 'Penata Tingkat I (III/d)'),
('199401011701211007', 'Teguh Hartono', 'Teknisi Komputer', 'Pengatur (II/c)'),
('19820121801051008', 'Ayu Setiawan', 'Analis Anggaran', 'Pengatur Tingkat I (II/d)'),
('199901091809011009', 'Bagus Maulana', 'Analis Perbendaharaan', 'Penata Muda (III/a)'),
('199405251105251010', 'Nur Susanto', 'Pelaksana Verifikasi', 'Penata Muda Tingkat I (III/b)'),
('199709091001091011', 'Yusuf Kusuma', 'Pengadministrasi Umum', 'Penata (III/c)'),
('198405251305051012', 'Fitri Firmansyah', 'Bendahara Pembantu', 'Penata Tingkat I (III/d)'),
('197905131909251013', 'Irfan Nugroho', 'Staf Akuntansi', 'Pengatur (II/c)'),
('198805211909211014', 'Wulan Anggraini', 'Pengelola Data', 'Pengatur Tingkat I (II/d)'),
('198805011305211015', 'Kurnia Gunawan', 'Teknisi Komputer', 'Penata Muda (III/a)'),
('19800505505091016', 'Putri Saputra', 'Analis Anggaran', 'Penata Muda Tingkat I (III/b)'),
('198905131701011017', 'Galih Putra', 'Analis Perbendaharaan', 'Penata (III/c)'),
('199209131705011018', 'Ratna Handayani', 'Pelaksana Verifikasi', 'Penata Tingkat I (III/d)'),
('198505171601171019', 'Surya Permana', 'Pengadministrasi Umum', 'Pengatur (II/c)'),
('19980517705171020', 'Indah Lubis', 'Bendahara Pembantu', 'Pengatur Tingkat I (II/d)')
ON CONFLICT (nip) DO NOTHING;
