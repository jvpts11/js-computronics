/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.install;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveInstallStateTest {

    private static LiveInstallState.Env env(final boolean mirror, final long now) {
        return new LiveInstallState.Env(List.of("sda", "sdb"), mirror, now, 100L);
    }

    private static LiveInstallState.Result run(final LiveInstallState st, final String line, final boolean mirror,
                                               final long now) {
        return st.run(line, env(mirror, now));
    }

    @Test
    void arch_fullSequence_completesOnReboot() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        assertTrue(run(st, "mkfs.ext4 /dev/sda", true, 0).ok());
        assertTrue(run(st, "mount /dev/sda /mnt", true, 0).ok());
        assertTrue(run(st, "pacstrap /mnt base linux", true, 0).ok());
        assertTrue(run(st, "genfstab -U /mnt >> /mnt/etc/fstab", true, 0).ok());
        assertTrue(run(st, "arch-chroot /mnt", true, 0).ok());
        assertEquals("[root@archiso /]#", st.prompt());
        assertTrue(run(st, "grub-install /dev/sda", true, 0).ok());
        assertTrue(run(st, "passwd", true, 0).ok());
        assertTrue(run(st, "exit", true, 0).ok());
        final LiveInstallState.Result reboot = run(st, "reboot", true, 0);
        assertTrue(reboot.ok());
        assertTrue(reboot.complete());
        assertEquals(0, st.targetIndex());
    }

    @Test
    void arch_pacstrapWithoutMount_isNotAMountpoint() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        final LiveInstallState.Result r = run(st, "pacstrap /mnt base linux", true, 0);
        assertFalse(r.ok());
        assertTrue(r.lines().get(0).contains("not a mountpoint"));
    }

    @Test
    void arch_pacstrapWithoutMirror_cannotResolveHost() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        run(st, "mkfs.ext4 /dev/sda", false, 0);
        run(st, "mount /dev/sda /mnt", false, 0);
        final LiveInstallState.Result r = run(st, "pacstrap /mnt base linux", false, 0);
        assertFalse(r.ok());
        assertTrue(r.lines().get(0).contains("Could not resolve host"));
    }

    @Test
    void mount_unformattedDevice_failsWithWrongFsType() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        final LiveInstallState.Result r = run(st, "mount /dev/sdb /mnt", true, 0);
        assertFalse(r.ok());
        assertTrue(r.lines().get(0).contains("wrong fs type"));
    }

    @Test
    void reboot_beforeBootloader_listsWhatIsMissing() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        run(st, "mkfs.ext4 /dev/sda", true, 0);
        run(st, "mount /dev/sda /mnt", true, 0);
        run(st, "pacstrap /mnt base linux", true, 0);
        final LiveInstallState.Result r = run(st, "reboot", true, 0);
        assertFalse(r.ok());
        assertFalse(r.complete());
        assertTrue(String.join("\n", r.lines()).contains("no bootloader"));
    }

    @Test
    void reboot_insideChroot_isRefused() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.ARCH);
        run(st, "mkfs.ext4 /dev/sda", true, 0);
        run(st, "mount /dev/sda /mnt", true, 0);
        run(st, "pacstrap /mnt base linux", true, 0);
        run(st, "arch-chroot /mnt", true, 0);
        assertFalse(run(st, "reboot", true, 0).ok());
    }

    @Test
    void gentoo_kernelBuildWaitsForCompileTicks() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.GENTOO);
        run(st, "mkfs.ext4 /dev/sdb", true, 0);
        run(st, "mount /dev/sdb /mnt", true, 0);
        assertTrue(run(st, "tar xpf stage3-amd64.tar.xz -C /mnt", true, 0).ok());
        assertTrue(run(st, "chroot /mnt", true, 0).ok());
        assertFalse(run(st, "emerge sys-kernel/gentoo-sources", true, 0).ok()); // not synced yet
        assertTrue(run(st, "emerge --sync", true, 0).ok());
        assertTrue(run(st, "emerge sys-kernel/gentoo-sources", true, 10).ok());
        assertFalse(run(st, "genkernel all", true, 50).ok());   // still compiling (ready at 110)
        assertTrue(run(st, "genkernel all", true, 120).ok());
        assertTrue(run(st, "grub-install /dev/sdb", true, 120).ok());
        assertTrue(run(st, "passwd", true, 120).ok());
        run(st, "exit", true, 120);
        assertTrue(run(st, "reboot", true, 120).complete());
        assertEquals(1, st.targetIndex());
    }

    @Test
    void serialize_roundTripsTheProgress() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.GENTOO);
        run(st, "mkfs.ext4 /dev/sda", true, 0);
        run(st, "mount /dev/sda /mnt", true, 0);
        run(st, "tar xpf stage3-amd64.tar.xz -C /mnt", true, 0);
        run(st, "chroot /mnt", true, 0);
        final LiveInstallState back = LiveInstallState.deserialize(st.serialize());
        assertEquals(st.prompt(), back.prompt());
        assertTrue(back.inChroot());
        assertEquals(0, back.targetIndex());
    }

    @Test
    void wrongDistroVerb_isCommandNotFound() {
        final LiveInstallState st = new LiveInstallState(LiveInstallState.Distro.GENTOO);
        assertTrue(run(st, "pacstrap /mnt base", true, 0).lines().get(0).contains("command not found"));
    }
}
