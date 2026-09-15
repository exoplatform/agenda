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
    <exo-drawer
      id="agendaPublishedCalendarsDrawer"
      ref="publishedCalendarsDrawer"
      :right="!$vuetify.rtl"
      disable-pull-to-refresh
      @closed="opened = false">
      <template slot="title">
        {{ $t('agenda.calendarPublish.settings.title') }}
      </template>
      <template slot="content">
        <div class="pa-4">
          <div class="text-subtitle mb-4">
            {{ $t('agenda.calendarPublish.settings.about') }}
          </div>
          <v-list class="pa-0">
            <v-list-item
              v-for="link in links"
              :key="link.calendarId"
              class="px-0 agenda-published-calendar">
              <v-list-item-content>
                <v-list-item-title class="d-flex align-center">
                  <v-icon
                    size="16"
                    class="me-2 text-light-color agenda-published-calendar-kind">
                    {{ isSpace(link) ? 'fas fa-users' : 'fas fa-user' }}
                  </v-icon>
                  <span class="text-truncate agenda-published-calendar-title">{{ titleOf(link) }}</span>
                  <span
                    v-if="ownerOf(link)"
                    class="ms-1 text-light-color text-no-wrap agenda-published-calendar-owner">
                    {{ ownerOf(link) }}
                  </span>
                </v-list-item-title>
                <v-list-item-subtitle class="agenda-published-calendar-date">
                  {{ publishedLine(link) }}
                </v-list-item-subtitle>
                <div
                  v-if="!link.active"
                  class="warning--text caption mt-1 agenda-published-calendar-stopped">
                  {{ stoppedLine(link) }}
                </div>
                <div class="d-flex flex-wrap mt-1">
                  <v-btn
                    v-if="link.active && link.url"
                    :disabled="busy"
                    class="primary--text text-none px-1 agenda-published-calendar-copy"
                    small
                    text
                    @click="copy(link)">
                    {{ $t('agenda.calendarPublish.settings.copyLink') }}
                  </v-btn>
                  <v-btn
                    :disabled="busy"
                    class="primary--text text-none px-1 agenda-published-calendar-save"
                    small
                    text
                    @click="ask('reset', link)">
                    {{ link.active ? $t('agenda.calendarPublish.reset') : $t('agenda.calendarPublish.createNew') }}
                  </v-btn>
                  <v-btn
                    :disabled="busy"
                    class="error--text text-none px-1 agenda-published-calendar-unpublish"
                    small
                    text
                    @click="ask('delete', link)">
                    {{ $t('agenda.calendarPublish.delete') }}
                  </v-btn>
                </div>
              </v-list-item-content>
            </v-list-item>
          </v-list>
        </div>
      </template>
    </exo-drawer>
    <exo-confirm-dialog
      ref="confirmDialog"
      :title="confirmTitle"
      :message="confirmMessage"
      :ok-label="confirmOkLabel"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="confirmed" />
  </div>
</template>

<script>
export default {
  props: {
    /**
     * The published calendars, read by the row that owns this drawer so both
     * show the same list at the same moment.
     */
    links: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    opened: false,
    busy: false,
    pendingAction: null,
    pendingLink: null,
  }),
  computed: {
    /**
     * @returns {String} the confirmation title of the pending action
     */
    confirmTitle() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarPublish.deleteConfirmTitle') : this.$t('agenda.calendarPublish.resetConfirmTitle');
    },
    /**
     * @returns {String} the confirmation message of the pending action
     */
    confirmMessage() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarPublish.deleteConfirmMessage') : this.$t('agenda.calendarPublish.resetConfirmMessage');
    },
    /**
     * @returns {String} the confirmation button label of the pending action
     */
    confirmOkLabel() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarPublish.delete') : this.$t('agenda.calendarPublish.reset');
    },
  },
  watch: {
    /**
     * Closes the drawer once the list it was opened on is empty: an empty panel
     * left open makes the user close it to find out whether anything happened.
     *
     * @param {Array} current the new list
     * @returns {void}
     */
    links(current) {
      if (this.opened && !current.length) {
        this.$refs.publishedCalendarsDrawer.close();
      }
    },
  },
  created() {
    this.$root.$on('agenda-published-calendars-drawer-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-published-calendars-drawer-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer.
     *
     * @returns {void}
     */
    open() {
      this.opened = true;
      this.$refs.publishedCalendarsDrawer.open();
    },
    /**
     * @param {Object} link a listed link
     * @returns {Boolean} whether its calendar belongs to a space
     */
    isSpace(link) {
      return link.calendarKind === 'SPACE';
    },
    /**
     * The calendar's title: its own name, "My calendar" for an unnamed default
     * personal calendar, else what agenda derives.
     *
     * @param {Object} link a listed link
     * @returns {String} the title
     */
    titleOf(link) {
      if (!this.isSpace(link) && link.systemCalendar) {
        return this.$t('agenda.myCalendar');
      }
      return link.calendarTitle || link.spaceDisplayName || '';
    },
    /**
     * Whose calendar it is: "(personal)", or the space's name when the title
     * does not already say it.
     *
     * @param {Object} link a listed link
     * @returns {String} the owner mention, empty when it would repeat the title
     */
    ownerOf(link) {
      if (!this.isSpace(link)) {
        return this.$t('agenda.calendarPublish.settings.personal');
      }
      return link.spaceDisplayName && link.spaceDisplayName !== link.calendarTitle ? `(${link.spaceDisplayName})` : '';
    },
    /**
     * When the calendar was published, and by whom for a space calendar.
     *
     * @param {Object} link a listed link
     * @returns {String} the line
     */
    publishedLine(link) {
      const date = link.createdDate && new Date(link.createdDate).toLocaleDateString(eXo.env.portal.language) || '';
      return this.isSpace(link)
        ? this.$t('agenda.calendarPublish.active', {0: this.creatorOf(link), 1: date})
        : this.$t('agenda.calendarPublish.settings.publishedOn', {0: date});
    },
    /**
     * Why a link stopped working.
     *
     * @param {Object} link a listed, stopped link
     * @returns {String} the line
     */
    stoppedLine(link) {
      return this.isSpace(link)
        ? this.$t('agenda.calendarPublish.deadSpace', {0: this.creatorOf(link)})
        : this.$t('agenda.calendarPublish.deadPersonal');
    },
    /**
     * @param {Object} link a listed link
     * @returns {String} the creator's name, or a neutral one
     */
    creatorOf(link) {
      return link.creatorName || this.$t('agenda.calendarPublish.unknownCreator');
    },
    /**
     * Asks before resetting (or publishing again) or unpublishing.
     *
     * @param {String} action reset or delete
     * @param {Object} link the link acted on
     * @returns {void}
     */
    ask(action, link) {
      this.pendingAction = action;
      this.pendingLink = link;
      this.$refs.confirmDialog.open();
    },
    /**
     * Runs the confirmed action, then tells the row and the rest of the page.
     *
     * @returns {Promise} resolved once done
     */
    confirmed() {
      const action = this.pendingAction;
      const link = this.pendingLink;
      this.pendingAction = null;
      this.pendingLink = null;
      if (!link) {
        return Promise.resolve();
      }
      this.busy = true;
      const request = action === 'delete'
        ? this.$calendarLinkService.deleteCalendarLink(link.calendarId)
        : this.$calendarLinkService.saveCalendarLink(link.calendarId);
      return request
        .then(() => {
          this.$root.$emit('alert-message',
            this.$t(action === 'delete' ? 'agenda.calendarPublish.unpublished' : 'agenda.calendarPublish.settings.resetDone'),
            'success');
          this.$emit('changed');
          this.$root.$emit('agenda-calendar-links-changed');
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.error'), 'error'))
        .finally(() => this.busy = false);
    },
    /**
     * Copies a link's URL to the clipboard, and says whether it worked.
     *
     * @param {Object} link a working, displayable link
     * @returns {Promise} resolved once reported
     */
    copy(link) {
      const clipboard = window.navigator && window.navigator.clipboard;
      const copied = clipboard && clipboard.writeText
        ? clipboard.writeText(link.url)
        : Promise.reject(new Error('No clipboard'));
      return copied
        .then(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.copied'), 'success'))
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.copyFailed'), 'warning'));
    },
  },
};
</script>
