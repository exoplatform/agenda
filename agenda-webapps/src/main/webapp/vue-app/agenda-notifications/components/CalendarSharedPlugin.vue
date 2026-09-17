<!--
Copyright (C) 2026 eXo Platform SAS.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <user-notification-template
    :notification="notification"
    :avatar-url="avatarUrl"
    :message="message"
    :url="agendaUrl"
    user-avatar>
    <template #actions>
      <div class="text-truncate agenda-calendar-shared-name">
        <v-icon size="14" class="me-1 mb-1">fa-calendar-alt</v-icon>
        {{ calendarName }}
      </div>
      <div class="mt-1">
        <v-btn
          :href="agendaUrl"
          color="primary"
          class="agenda-calendar-shared-open"
          elevation="0"
          small
          outlined>
          <span class="text-none">
            {{ $t('Notification.agenda.calendar.shared.view') }}
          </span>
        </v-btn>
      </div>
    </template>
  </user-notification-template>
</template>
<script>
/**
 * The "a calendar was shared with you" notification (EXO-90357) in the
 * drawer and on the notifications page: the owner's avatar, the sentence
 * naming the owner and the calendar, the calendar's name and a button to
 * the agenda, where the calendar sits under "Shared with me". Everything is
 * read from what the plugin stored, and from the sender the notification
 * carries.
 */
export default {
  props: {
    notification: {
      type: Object,
      default: null,
    },
  },
  computed: {
    /**
     * The owner, as the notification's sender.
     *
     * @returns {Object} the sender's profile, or null
     */
    owner() {
      return this.notification?.from || null;
    },
    /**
     * The owner's name: the sender's, else the one stored when the calendar
     * was shared.
     *
     * @returns {String} the name
     */
    ownerName() {
      return this.owner?.fullname || this.notification?.parameters?.ownerName || this.notification?.parameters?.ownerUsername || '';
    },
    /**
     * The owner's avatar: the sender's, else the one the platform serves
     * for the stored username.
     *
     * @returns {String} the avatar URL, or null
     */
    avatarUrl() {
      if (this.owner?.avatar) {
        return this.owner.avatar;
      }
      const username = this.notification?.parameters?.ownerUsername;
      return username ? `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/${username}/avatar` : null;
    },
    /**
     * The shared calendar's name.
     *
     * @returns {String} the name
     */
    calendarName() {
      return this.notification?.parameters?.calendarName || '';
    },
    /**
     * Where the button and the row lead: the agenda, whose "Shared with me"
     * section lists the calendar. Stored when the calendar was shared, with
     * the agenda page as the fallback.
     *
     * @returns {String} the URL
     */
    agendaUrl() {
      return this.notification?.parameters?.Url || `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/agenda`;
    },
    /**
     * The sentence, with the owner and the calendar emphasised the way the
     * other agenda notifications do.
     *
     * @returns {String} the sentence, as HTML the template sanitises
     */
    message() {
      return this.$t('Notification.agenda.calendar.shared', {
        0: `<a class="user-name font-weight-bold">${this.escape(this.ownerName)}</a>`,
        1: `<span class="font-weight-bold">${this.escape(this.calendarName)}</span>`,
      });
    },
  },
  methods: {
    /**
     * A name as text inside the sentence's markup.
     *
     * @param {String} text the name
     * @returns {String} the name with its markup characters escaped
     */
    escape(text) {
      return String(text || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    },
  },
};
</script>
