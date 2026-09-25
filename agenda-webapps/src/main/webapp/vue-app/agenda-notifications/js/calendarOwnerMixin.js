/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */

/**
 * The calendar an agenda notification belongs to, resolved from its owner
 * identity: a space calendar is pictured by its space's avatar, a personal
 * calendar by its owner's avatar.
 */
export default {
  data: () => ({
    space: null,
    personalCalendar: false,
    ownerAvatarUrl: null,
  }),
  created() {
    if (this.notification?.space) {
      this.space = this.notification.space;
    } else {
      this.$identityService.getIdentityById(this.notification?.parameters?.ownerId).then(identity => {
        if (identity?.space) {
          this.space = identity.space;
        } else if (identity) {
          this.personalCalendar = true;
          this.ownerAvatarUrl = identity.profile?.avatar;
        }
      });
    }
  },
  computed: {
    calendarAvatarUrl() {
      return this.personalCalendar ? this.ownerAvatarUrl : this.space?.avatarUrl;
    },
  },
};
