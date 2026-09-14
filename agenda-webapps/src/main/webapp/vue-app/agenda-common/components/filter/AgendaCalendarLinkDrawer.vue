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
  <!--
    The private iCal link of one calendar (EXO-90252). One instance for the whole
    application, mounted beside the other calendar drawers in Agenda.vue and
    opened by a root event: never inside a calendar row, because a row can go
    away while the drawer is open (a deleted calendar, a filtered space list),
    and exo-drawer destroyed while open leaves its overlay behind with nothing
    able to dismiss it (EXO-90239).
  -->
  <div>
    <exo-drawer
      ref="calendarLinkDrawer"
      :loading="busy"
      class="agendaCalendarLinkDrawer"
      right
      @closed="forgetUrl">
      <template slot="title">
        {{ $t('agenda.calendarLink.title') }}
      </template>
      <template slot="content">
        <div v-if="calendar" class="pa-4">
          <div class="text-sub-title font-weight-bold mb-2 text-truncate">{{ calendarName }}</div>
          <p class="text-light-color">{{ $t('agenda.calendarLink.explanation') }}</p>
          <div
            v-if="state === 'loading'"
            class="d-flex justify-center py-4">
            <v-progress-circular
              color="primary"
              size="24"
              width="2"
              indeterminate />
          </div>
          <div
            v-else-if="state === 'created'"
            class="agenda-calendar-link-created">
            <v-label for="agendaCalendarLinkUrl">
              {{ $t('agenda.calendarLink.urlLabel') }}
            </v-label>
            <div class="d-flex align-center mt-2">
              <v-text-field
                id="agendaCalendarLinkUrl"
                ref="urlInput"
                :value="url"
                class="pt-0 flex-grow-1 agenda-calendar-link-url"
                readonly
                outlined
                dense
                hide-details
                @focus="selectUrl" />
              <v-btn
                class="btn ms-2 agenda-calendar-link-copy"
                @click="copy">
                {{ $t('agenda.calendarLink.copy') }}
              </v-btn>
            </div>
            <p class="caption mt-2">{{ $t('agenda.calendarLink.shownOnce') }}</p>
          </div>
          <div
            v-else-if="state === 'active'"
            class="agenda-calendar-link-active">
            <p class="mb-1">{{ activeMessage }}</p>
            <p class="caption">{{ $t('agenda.calendarLink.activeHint') }}</p>
          </div>
          <p
            v-else-if="state === 'dead'"
            class="agenda-calendar-link-dead error--text">
            {{ deadMessage }}
          </p>
          <p
            v-else
            class="agenda-calendar-link-none">
            {{ $t('agenda.calendarLink.none') }}
          </p>
        </div>
      </template>
      <template slot="footer">
        <div class="d-flex">
          <v-btn
            v-if="hasLink"
            :disabled="busy"
            class="btn agenda-calendar-link-delete"
            @click="confirmDelete">
            {{ $t('agenda.calendarLink.delete') }}
          </v-btn>
          <v-spacer />
          <v-btn
            v-if="state !== 'loading'"
            :disabled="busy"
            class="btn btn-primary agenda-calendar-link-save"
            @click="create">
            {{ saveLabel }}
          </v-btn>
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
  data: () => ({
    calendar: null,
    status: null,
    url: '',
    loading: false,
    saving: false,
    pendingAction: null,
  }),
  computed: {
    /**
     * Whether a request is running.
     *
     * @returns {Boolean} true while loading or saving
     */
    busy() {
      return this.loading || this.saving;
    },
    /**
     * What the drawer shows: loading, the URL just created, an active link, a
     * link that stopped working, or no link.
     *
     * @returns {String} loading, created, active, dead or none
     */
    state() {
      if (this.loading) {
        return 'loading';
      } else if (this.url) {
        return 'created';
      } else if (!this.status || !this.status.exists) {
        return 'none';
      }
      return this.status.active ? 'active' : 'dead';
    },
    /**
     * Whether the calendar has a link, working or not.
     *
     * @returns {Boolean} true when a link exists
     */
    hasLink() {
      return !this.loading && !!this.status && !!this.status.exists;
    },
    /**
     * Whether the calendar belongs to a space.
     *
     * @returns {Boolean} true for a space calendar
     */
    isSpaceCalendar() {
      const owner = this.calendar && this.calendar.owner;
      return !!owner && (owner.providerId === 'space' || !!owner.space);
    },
    /**
     * The calendar's name as its row shows it.
     *
     * @returns {String} the name
     */
    calendarName() {
      const calendar = this.calendar || {};
      const owner = calendar.owner || {};
      const profile = owner.space || owner.profile || {};
      return calendar.name || profile.displayName || profile.fullname || calendar.title || this.$t('agenda.myCalendar');
    },
    /**
     * Who created the link, or a neutral name when the creator cannot be
     * resolved any more.
     *
     * @returns {String} the creator's name
     */
    creatorName() {
      return this.status && this.status.creatorName || this.$t('agenda.calendarLink.unknownCreator');
    },
    /**
     * The sentence describing an active link.
     *
     * @returns {String} who created it and when
     */
    activeMessage() {
      const createdDate = this.status && this.status.createdDate
        && new Date(this.status.createdDate).toLocaleDateString(eXo.env.portal.language) || '';
      return this.$t('agenda.calendarLink.active', {0: this.creatorName, 1: createdDate});
    },
    /**
     * The sentence describing a link that stopped working.
     *
     * @returns {String} why it stopped
     */
    deadMessage() {
      return this.isSpaceCalendar
        && this.$t('agenda.calendarLink.deadSpace', {0: this.creatorName})
        || this.$t('agenda.calendarLink.deadPersonal');
    },
    /**
     * The label of the main button: creating a first link, replacing a dead
     * one, or resetting a working one.
     *
     * @returns {String} the label
     */
    saveLabel() {
      if (this.state === 'dead') {
        return this.$t('agenda.calendarLink.createNew');
      } else if (this.hasLink) {
        return this.$t('agenda.calendarLink.reset');
      }
      return this.$t('agenda.calendarLink.create');
    },
    /**
     * @returns {String} the confirmation title of the pending action
     */
    confirmTitle() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarLink.deleteConfirmTitle') : this.$t('agenda.calendarLink.resetConfirmTitle');
    },
    /**
     * @returns {String} the confirmation message of the pending action
     */
    confirmMessage() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarLink.deleteConfirmMessage') : this.$t('agenda.calendarLink.resetConfirmMessage');
    },
    /**
     * @returns {String} the confirmation button label of the pending action
     */
    confirmOkLabel() {
      return this.pendingAction === 'delete' ? this.$t('agenda.calendarLink.delete') : this.$t('agenda.calendarLink.reset');
    },
  },
  created() {
    this.$root.$on('agenda-calendar-link-drawer-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-link-drawer-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer on a calendar and reads its link.
     *
     * @param {Object} calendar the calendar whose link is managed
     * @returns {Promise} resolved once the status is read
     */
    open(calendar) {
      this.calendar = calendar;
      this.status = null;
      this.url = '';
      this.pendingAction = null;
      this.$refs.calendarLinkDrawer.open();
      return this.load();
    },
    /**
     * Reads the status of the calendar's link.
     *
     * @returns {Promise} resolved once read
     */
    load() {
      this.loading = true;
      return this.$calendarLinkService.getCalendarLink(this.calendar.id)
        .then(status => this.status = status)
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarLink.loadError'), 'error'))
        .finally(() => this.loading = false);
    },
    /**
     * Creates the link, asking first when it would replace an existing one.
     *
     * @returns {Promise|void} the creation when no confirmation is needed
     */
    create() {
      if (this.hasLink || this.url) {
        this.pendingAction = 'reset';
        this.$refs.confirmDialog.open();
        return;
      }
      return this.save();
    },
    /**
     * Asks before deleting the link.
     *
     * @returns {void}
     */
    confirmDelete() {
      this.pendingAction = 'delete';
      this.$refs.confirmDialog.open();
    },
    /**
     * Runs the action the user confirmed.
     *
     * @returns {Promise} the deletion or the creation
     */
    confirmed() {
      const action = this.pendingAction;
      this.pendingAction = null;
      return action === 'delete' ? this.remove() : this.save();
    },
    /**
     * Creates or replaces the link and shows its URL, the only time it is
     * available.
     *
     * @returns {Promise} resolved once saved
     */
    save() {
      this.saving = true;
      return this.$calendarLinkService.saveCalendarLink(this.calendar.id)
        .then(status => {
          this.status = status;
          this.url = status && status.url || '';
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarLink.error'), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Deletes the link. The drawer stays open on the calendar, now without a
     * link, so a new one can be created from the same place.
     *
     * @returns {Promise} resolved once deleted
     */
    remove() {
      this.saving = true;
      return this.$calendarLinkService.deleteCalendarLink(this.calendar.id)
        .then(() => {
          this.status = {exists: false, active: false};
          this.url = '';
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarLink.error'), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Copies the URL to the clipboard, and says whether it worked: a browser
     * refusing clipboard access leaves the URL selected in its field instead.
     *
     * @returns {Promise} resolved once reported
     */
    copy() {
      const clipboard = window.navigator && window.navigator.clipboard;
      const copied = clipboard && clipboard.writeText
        ? clipboard.writeText(this.url)
        : Promise.reject(new Error('No clipboard'));
      return copied
        .then(() => this.$root.$emit('alert-message', this.$t('agenda.calendarLink.copied'), 'success'))
        .catch(() => {
          this.selectUrl();
          this.$root.$emit('alert-message', this.$t('agenda.calendarLink.copyFailed'), 'warning');
        });
    },
    /**
     * Selects the whole URL in its field, so it can be copied by hand.
     *
     * @returns {void}
     */
    selectUrl() {
      const field = this.$refs.urlInput;
      const input = field && field.$el && field.$el.querySelector && field.$el.querySelector('input');
      if (input) {
        input.select();
      }
    },
    /**
     * Forgets the URL once the drawer closes: it was shown once, and reopening
     * the drawer shows the link's status, never the URL again.
     *
     * @returns {void}
     */
    forgetUrl() {
      this.url = '';
    },
  },
};
</script>
