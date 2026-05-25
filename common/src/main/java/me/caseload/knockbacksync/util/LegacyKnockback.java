/*
 * Legacy (Minecraft 1.8) melee knockback formula.
 *
 * This file is a port of the knockback calculation from BukkitOldCombatMechanics
 * (kernitus/BukkitOldCombatMechanics), module ModulePlayerKnockback, which is
 * licensed under the Mozilla Public License, v. 2.0:
 *
 *   This Source Code Form is subject to the terms of the Mozilla Public
 *   License, v. 2.0. If a copy of the MPL was not distributed with this
 *   file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * The MPL-2.0 does not carry the "Incompatible With Secondary Licenses" notice
 * (Exhibit B), so per MPL-2.0 §3.3 this Covered Software may be distributed as
 * part of a Larger Work under the GNU General Public License v3.0-or-later that
 * governs the rest of KnockbackSync. The terms of the MPL continue to apply to
 * this file. See the NOTICE file at the project root for attribution details.
 */
package me.caseload.knockbacksync.util;

import com.github.retrooper.packetevents.util.Vector3d;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.player.PlatformPlayer;
import me.caseload.knockbacksync.player.PlayerData;

/**
 * Computes the exact 1.8-style melee knockback vector applied to a victim, so that
 * KnockbackSync can own the final melee velocity (horizontal + vertical) and then
 * latency-correct it, rather than letting vanilla knockback win.
 *
 * <p>The math is a faithful reproduction of OldCombatMechanics' legacy player
 * knockback so that values are identical to running OCM's old-player-knockback
 * module, while KnockbackSync remains the single writer of the velocity.</p>
 */
public final class LegacyKnockback {

    private LegacyKnockback() {
    }

    /**
     * @param victim       the player receiving knockback
     * @param attacker     the attacking player
     * @param victimData   the victim's tracked data (for knockback resistance attribute)
     * @param config       the plugin configuration (legacy knockback tunables)
     * @return the 1.8-style velocity to apply to the victim, before latency Y-correction
     */
    public static Vector3d compute(PlatformPlayer victim, PlatformPlayer attacker,
                                   PlayerData victimData, ConfigManager config) {
        final double knockbackHorizontal = config.getLegacyKnockbackHorizontal();
        final double knockbackVertical = config.getLegacyKnockbackVertical();
        final double knockbackVerticalLimit = config.getLegacyKnockbackVerticalLimit();
        final double knockbackExtraHorizontal = config.getLegacyKnockbackExtraHorizontal();
        final double knockbackExtraVertical = config.getLegacyKnockbackExtraVertical();
        final boolean netheriteKnockbackResistance = config.isLegacyKnockbackResistanceEnabled();

        // Figure out base knockback direction (attacker -> victim) with vanilla jitter
        // when the two are stacked on the exact same column.
        double d0 = attacker.getX() - victim.getX();
        double d1;
        for (d1 = attacker.getZ() - victim.getZ(); d0 * d0 + d1 * d1 < 1.0E-4D;
             d1 = (Math.random() - Math.random()) * 0.01D) {
            d0 = (Math.random() - Math.random()) * 0.01D;
        }

        final double magnitude = Math.sqrt(d0 * d0 + d1 * d1);

        // Player velocity before any friction is applied (pre-knockback motion).
        final Vector3d v = victim.getVelocity();

        // Apply friction (halve), then add base knockback.
        double vx = (v.getX() / 2) - (d0 / magnitude * knockbackHorizontal);
        double vy = (v.getY() / 2) + knockbackVertical;
        double vz = (v.getZ() / 2) - (d1 / magnitude * knockbackHorizontal);

        // Bonus knockback for sprinting and the Knockback enchantment.
        int bonusKnockback = attacker.getWeaponKnockbackLevel();
        if (attacker.isSprinting()) {
            bonusKnockback++;
        }

        // NOTE: vanilla caps vertical BEFORE adding the sprint/enchant bonus vertical.
        if (vy > knockbackVerticalLimit) {
            vy = knockbackVerticalLimit;
        }

        if (bonusKnockback > 0) {
            final float yaw = attacker.getYaw();
            vx += -Math.sin(yaw * 3.1415927F / 180.0F) * (float) bonusKnockback * knockbackExtraHorizontal;
            vy += knockbackExtraVertical;
            vz += Math.cos(yaw * 3.1415927F / 180.0F) * (float) bonusKnockback * knockbackExtraHorizontal;
        }

        if (netheriteKnockbackResistance) {
            // Each piece of netherite armour yields 10% horizontal resistance.
            final double resistance = 1 - victimData.getKnockbackResistanceAttribute();
            vx *= resistance;
            vz *= resistance;
        }

        return new Vector3d(vx, vy, vz);
    }
}
