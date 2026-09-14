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
      @closed="forget">
      <template slot="title">
        {{ $t('agenda.calendarPublish.title') }}
      </template>
      <template slot="content">
        <div v-if="calendar" class="pa-4">
          <!-- The title is the bare verb, so the calendar being published is named here. -->
          <p class="font-weight-bold mb-2 agenda-calendar-link-name">{{ calendarName }}</p>
          <p class="text-light-color">{{ $t('agenda.calendarPublish.explanation') }}</p>
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
            v-else-if="state === 'active'"
            class="agenda-calendar-link-active">
            <p class="mb-2">{{ activeMessage }}</p>
            <v-label for="agendaCalendarLinkUrl">
              {{ $t('agenda.calendarPublish.urlLabel') }}
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
                {{ $t('agenda.calendarPublish.copy') }}
              </v-btn>
            </div>
          </div>
          <div
            v-else-if="state === 'undisplayable'"
            class="agenda-calendar-link-undisplayable">
            <p class="mb-1">{{ activeMessage }}</p>
            <p class="warning--text">{{ $t('agenda.calendarPublish.undisplayable') }}</p>
          </div>
          <p
            v-else-if="state === 'dead'"
            class="agenda-calendar-link-dead error--text">
            {{ deadMessage }}
          </p>
          <p
            v-else
            class="agenda-calendar-link-none">
            {{ $t('agenda.calendarPublish.none') }}
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
            {{ $t('agenda.calendarPublish.delete') }}
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
     * The link's URL, as the server answers it: only while the link works and
     * can be displayed, and only to someone who manages it.
     *
     * @returns {String} the URL, empty when there is none to show
     */
    url() {
      return this.status && this.status.url || '';
    },
    /**
     * What the drawer shows: loading, a working link with its URL, a working
     * link whose URL can no longer be displayed, a link that stopped working,
     * or no link.
     *
     * @returns {String} loading, active, undisplayable, dead or none
     */
    state() {
      if (this.loading) {
        return 'loading';
      } else if (!this.status || !this.status.exists) {
        return 'none';
      } else if (!this.status.active) {
        return 'dead';
      }
      return this.url ? 'active' : 'undisplayable';
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
      return this.status && this.status.creatorName || this.$t('agenda.calendarPublish.unknownCreator');
    },
    /**
     * The sentence saying who created a working link, and when.
     *
     * @returns {String} the sentence
     */
    activeMessage() {
      const createdDate = this.status && this.status.createdDate
        && new Date(this.status.createdDate).toLocaleDateString(eXo.env.portal.language) || '';
      return this.$t('agenda.calendarPublish.active', {0: this.creatorName, 1: createdDate});
    },
    /**
     * The sentence describing a link that stopped working.
     *
     * @returns {String} why it stopped
     */
    deadMessage() {
      return this.isSpaceCalendar
        && this.$t('agenda.calendarPublish.deadSpace', {0: this.creatorName})
        || this.$t('agenda.calendarPublish.deadPersonal');
    },
    /**
     * The label of the main button: creating a first link, replacing a dead
     * one, or resetting a working one.
     *
     * @returns {String} the label
     */
    saveLabel() {
      if (this.state === 'dead') {
        return this.$t('agenda.calendarPublish.createNew');
      } else if (this.hasLink) {
        return this.$t('agenda.calendarPublish.reset');
      }
      return this.$t('agenda.calendarPublish.create');
    },
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
  created() {
    this.$root.$on('agenda-calendar-link-drawer-open', this.open);
    this.$root.$on('agenda-calendar-link-unpublish', this.askUnpublish);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-link-drawer-open', this.open);
    this.$root.$off('agenda-calendar-link-unpublish', this.askUnpublish);
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
      this.pendingAction = null;
      this.$refs.calendarLinkDrawer.open();
      return this.load();
    },
    /**
     * Reads the status of the calendar's link, URL included when the server
     * gives it.
     *
     * @returns {Promise} resolved once read
     */
    load() {
      this.loading = true;
      return this.$calendarLinkService.getCalendarLink(this.calendar.id)
        .then(status => this.status = status)
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.loadError'), 'error'))
        .finally(() => this.loading = false);
    },
    /**
     * Creates the link, asking first when it would replace an existing one —
     * working, undisplayable or dead alike.
     *
     * @returns {Promise|void} the creation when no confirmation is needed
     */
    create() {
      if (this.hasLink) {
        this.pendingAction = 'reset';
        this.$refs.confirmDialog.open();
        return;
      }
      return this.save();
    },
    /**
     * Unpublishes a calendar from a menu, without opening the drawer: the same
     * confirmation, then the same deletion and its success message.
     *
     * @param {Object} calendar the calendar to unpublish
     * @returns {void}
     */
    askUnpublish(calendar) {
      this.calendar = calendar;
      this.pendingAction = 'delete';
      this.$refs.confirmDialog.open();
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
     * Creates or replaces the link and shows the status the server answers,
     * with the new URL.
     *
     * @returns {Promise} resolved once saved
     */
    save() {
      this.saving = true;
      return this.$calendarLinkService.saveCalendarLink(this.calendar.id)
        .then(status => {
          this.status = status;
          this.notifyChanged();
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.error'), 'error'))
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
          this.status = {exists: false, active: false, displayable: false};
          this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.unpublished'), 'success');
          this.notifyChanged();
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.error'), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Tells every list drawing publishing states that a link changed, so rows,
     * menus and the settings row read them again.
     *
     * @returns {void}
     */
    notifyChanged() {
      this.$root.$emit('agenda-calendar-links-changed');
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
        .then(() => this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.copied'), 'success'))
        .catch(() => {
          this.selectUrl();
          this.$root.$emit('alert-message', this.$t('agenda.calendarPublish.copyFailed'), 'warning');
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
     * Forgets the link once the drawer closes, URL included: the next opening
     * asks the server again, which is what decides whether the URL may still be
     * shown.
     *
     * @returns {void}
     */
    forget() {
      this.status = null;
    },
  },
};
</script>
