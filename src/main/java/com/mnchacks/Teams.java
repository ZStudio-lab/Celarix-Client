package com.mnchacks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

/**
 * Team awareness helper.
 *
 * <p>Bed Wars teams are exposed through the scoreboard. When the server puts
 * two players on the same team (the usual case — the client sees the team the
 * server assigns, including Hypixel's coloured side-teams) we treat them as
 * allies. If the server has no teams at all, everyone is an enemy, which is the
 * correct conservative default.</p>
 *
 * <p>A colour fallback covers servers that colour names but do not use real
 * scoreboard teams.</p>
 */
public final class Teams {

	private Teams() {
	}

	/** True if {@code entity} is on the same team as {@code self}. */
	public static boolean isTeammate(Entity entity, PlayerEntity self) {
		if (self == null || !(entity instanceof PlayerEntity other)) {
			return false;
		}
		if (other == self) {
			return true;
		}

		Team ownTeam = self.getScoreboardTeam();
		Team otherTeam = other.getScoreboardTeam();
		if (ownTeam != null && otherTeam != null) {
			return ownTeam.isEqual(otherTeam);
		}

		Formatting ownColor = colorOf(self);
		Formatting otherColor = colorOf(other);
		return ownColor != null && ownColor == otherColor;
	}

	/**
	 * True if {@code entity} should be treated as a hostile target for
	 * {@code self}: a hostile mob, or a player who is not a teammate.
	 */
	public static boolean isEnemy(Entity entity, PlayerEntity self) {
		if (entity == self) {
			return false;
		}
		if (entity instanceof PlayerEntity player) {
			return !player.isSpectator() && !isTeammate(player, self)
					&& !FriendsModule.isFriend(player.getName().getString());
		}
		return entity instanceof Monster;
	}

	/** The team colour, or null when the player is not on a team. */
	private static Formatting colorOf(PlayerEntity player) {
		Team team = player.getScoreboardTeam();
		if (team == null) {
			return null;
		}
		Formatting color = team.getColor();
		if (color == null || color == Formatting.RESET) {
			return null;
		}
		return color;
	}
}
