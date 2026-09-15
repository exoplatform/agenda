<!--
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
-->
<template>
  <div>
    <v-list-item v-if="links.length">
      <v-list-item-content>
        <!-- text-color, like the other calendar rows on this page -->
        <v-list-item-title class="text-color">
          {{ $t('agenda.calendarPublish.settings.title') }}
        </v-list-item-title>
        <v-list-item-subtitle>
          <span>
            {{ $t('agenda.calendarPublish.settings.count', {0: links.length}) }}
          </span>
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action>
        <v-btn
          :aria-label="$t('agenda.calendarPublish.settings.manage')"
          :title="$t('agenda.calendarPublish.settings.manage')"
          icon
          @click="$root.$emit('agenda-published-calendars-drawer-open')">
          <v-icon size="20" class="icon-default-color">fa-edit</v-icon>
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <!--
      The drawer lives beside the row, not inside it: the row goes away when the
      last calendar is unpublished, which is an action taken from the drawer.
      exo-drawer destroyed while open leaves its page overlay behind with nothing
      able to dismiss it (EXO-90239); kept mounted, the drawer sees its list
      empty and closes itself the normal way.
    -->
    <agenda-user-published-calendars-drawer
      :links="links"
      @changed="retrieveLinks" />
  </div>
</template>

<script>
export default {
  data: () => ({
    links: [],
  }),
  created() {
    this.$root.$on('agenda-calendar-links-changed', this.retrieveLinks);
    this.$root.$on('agenda-settings-refresh', this.retrieveLinks);
    this.retrieveLinks();
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-links-changed', this.retrieveLinks);
    this.$root.$off('agenda-settings-refresh', this.retrieveLinks);
  },
  methods: {
    /**
     * Reads the published calendars: every calendar the user manages a link for
     * and that has one, working or stopped.
     *
     * A failure leaves the row absent rather than showing it empty: the page
     * must not be held up by it, the user came for the other rows.
     *
     * @returns {Promise} resolves once read or given up on
     */
    retrieveLinks() {
      return this.$calendarLinkService.getCalendarLinks(true)
        .then(links => this.links = links || [])
        .catch(error => {
          console.error('cannot read the published calendars', error);
          this.links = [];
        });
    },
  },
};
</script>
