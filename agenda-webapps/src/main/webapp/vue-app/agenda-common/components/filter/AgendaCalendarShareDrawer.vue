/*
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
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    Sharing one personal calendar with colleagues (EXO-90357). One instance for
    the whole application, mounted beside the other calendar drawers in
    Agenda.vue and opened by a root event — never inside a calendar row, for
    the reason the link drawer is not (EXO-90239).

    Agenda owns the share: the record is written here whether or not the owner
    connected a CalDAV account. Delivery to a calendar server is invisible to
    the person sharing: no chip, no warning, no retry — a failed delivery is
    logged server-side and the share stands in eXo. One list of colleagues: a
    share made directly on a server for a colleague of this deployment is
    recorded silently when the drawer opens and listed like any other. Access
    held on a server by principals that are not eXo users is listed apart,
    read-only, read live from the channel, with Remove when the server allows
    it.
  -->
  <div>
    <exo-drawer
      ref="calendarShareDrawer"
      :loading="busy"
      class="agendaCalendarShareDrawer"
      right
      @closed="forget">
      <template slot="title">
        {{ $t('agenda.calendarShare.title') }}
      </template>
      <template slot="content">
        <div v-if="calendar" class="pa-4">
          <p class="font-weight-bold mb-2 agenda-calendar-share-name">{{ calendarName }}</p>
          <p class="text-light-color">{{ $t('agenda.calendarShare.explanation') }}</p>
          <!--
            The suggester answers users only: a share names a colleague, never
            a space or a group in this version, and the owner is left out by
            the ignore list so they cannot share with themselves.
          -->
          <exo-identity-suggester
            ref="shareeSuggester"
            v-model="sharee"
            :labels="suggesterLabels"
            :ignore-items="ignoredItems"
            :search-options="searchOptions"
            :disabled="busy"
            name="calendarSharee"
            class="agenda-calendar-share-suggester"
            include-users
            dense />
          <p
            v-if="error"
            class="error--text caption agenda-calendar-share-error">
            {{ error }}
          </p>
          <div
            v-if="loading"
            class="d-flex justify-center py-4">
            <v-progress-circular
              color="primary"
              size="24"
              width="2"
              indeterminate />
          </div>
          <template v-else>
            <p
              v-if="!shares.length && !externalShares.length"
              class="text-light-color mt-4 agenda-calendar-share-none">
              {{ $t('agenda.calendarShare.none') }}
            </p>
            <!--
              The one list of colleagues: a share made in eXo and a share made
              on the calendar server directly look the same, since the latter
              is recorded in eXo, silently, when this list is read.
            -->
            <template v-if="shares.length">
              <p class="font-weight-bold mt-4 mb-1 agenda-calendar-share-sharees-title">
                {{ $t('agenda.calendarShare.sharedWith') }}
              </p>
              <v-list class="pa-0" dense>
                <v-list-item
                  v-for="share in shares"
                  :key="share.shareeIdentityId"
                  class="px-0 agenda-calendar-sharee">
                  <v-list-item-avatar size="32" class="me-2">
                    <exo-user-avatar
                      :profile-id="share.username"
                      :name="share.displayName"
                      :size="32"
                      avatar />
                  </v-list-item-avatar>
                  <v-list-item-content>
                    <v-list-item-title class="agenda-calendar-sharee-name">
                      {{ share.displayName || share.username }}
                      <span
                        v-if="share.disabled"
                        class="text-light-color caption ms-1 agenda-calendar-sharee-disabled">
                        {{ $t('agenda.calendarShare.disabledSharee') }}
                      </span>
                    </v-list-item-title>
                    <!--
                      Whether a channel also carries the share to a calendar
                      server is invisible here: "shared with" means the colleague
                      can see the calendar, however it reaches them.
                    -->
                    <v-list-item-subtitle>
                      <span class="agenda-calendar-share-access">{{ $t('agenda.calendarShare.access.view') }}</span>
                    </v-list-item-subtitle>
                  </v-list-item-content>
                  <v-list-item-action class="d-flex flex-row align-center my-0">
                    <v-btn
                      :disabled="busy"
                      :title="$t('agenda.calendarShare.unshare')"
                      :aria-label="$t('agenda.calendarShare.unshareOf', {0: share.displayName || share.username})"
                      class="agenda-calendar-share-unshare"
                      icon
                      small
                      @click="confirmUnshare(share)">
                      <v-icon size="16">fas fa-times</v-icon>
                    </v-btn>
                  </v-list-item-action>
                </v-list-item>
              </v-list>
            </template>
            <!--
              Access held outside eXo, for the owner's information: an address
              outside eXo, the whole server, a published link, a colleague who
              can edit. Read-only here, but for the grants the channel lets eXo
              remove on the server.
            -->
            <template v-if="externalShares.length">
              <p class="font-weight-bold mt-4 mb-1 agenda-calendar-share-external-title">
                {{ $t('agenda.calendarShare.alsoHasAccess') }}
              </p>
              <v-list class="pa-0" dense>
                <v-list-item
                  v-for="external in externalShares"
                  :key="`${external.channelId}:${external.externalId}`"
                  class="px-0 agenda-calendar-external-share">
                  <v-list-item-content>
                    <v-list-item-title class="agenda-calendar-external-name">
                      {{ externalName(external) }}
                      <span
                        v-if="external.email && external.email !== externalName(external)"
                        class="text-light-color caption ms-1 agenda-calendar-external-email">
                        {{ external.email }}
                      </span>
                    </v-list-item-title>
                    <v-list-item-subtitle>
                      <span class="me-2 agenda-calendar-share-access">
                        {{ external.readOnly === false ? $t('agenda.calendarShare.access.edit') : $t('agenda.calendarShare.access.view') }}
                      </span>
                      <span class="text-light-color caption">
                        {{ $t('agenda.calendarShare.onServer', {0: channelLabel(external.channelId)}) }}
                      </span>
                    </v-list-item-subtitle>
                  </v-list-item-content>
                  <v-list-item-action
                    v-if="external.removable"
                    class="d-flex flex-row align-center my-0">
                    <v-btn
                      :disabled="busy"
                      :title="$t('agenda.calendarShare.removeExternal')"
                      :aria-label="$t('agenda.calendarShare.removeExternalOf', {0: externalName(external)})"
                      class="agenda-calendar-share-remove-external"
                      icon
                      small
                      @click="removeExternal(external)">
                      <v-icon size="16">fas fa-times</v-icon>
                    </v-btn>
                  </v-list-item-action>
                </v-list-item>
              </v-list>
            </template>
          </template>
        </div>
      </template>
      <template slot="footer">
        <div class="d-flex">
          <v-spacer />
          <v-btn
            :disabled="busy"
            class="btn agenda-calendar-share-close"
            @click="close">
            {{ $t('agenda.button.close') }}
          </v-btn>
        </div>
      </template>
    </exo-drawer>
    <!--
      One dialog, two questions: revoking a share, and sharing a calendar
      that receives copies of the owner's eXo meetings (EXO-90345) — the
      titles, descriptions and spaces of every meeting they attend, private
      spaces included. That one is a title only, no explanation, asked once
      per share and only when a channel says the calendar holds copies.
    -->
    <exo-confirm-dialog
      ref="confirmDialog"
      :title="confirmTitle"
      :message="confirmMessage"
      :ok-label="confirmOkLabel"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="confirmed"
      @dialog-closed="cancelled" />
  </div>
</template>

<script>
export default {
  data: () => ({
    calendar: null,
    shares: [],
    externalShares: [],
    sharee: null,
    loading: false,
    saving: false,
    error: null,
    toUnshare: null,
    // Whether a channel copies the owner's eXo meetings into this calendar,
    // as the listing answered: sharing then asks first
    meetingCopies: false,
    // The colleague the suggester picked while the meeting-copies question is
    // open; shared with on OK, forgotten on cancel
    pendingSharee: null,
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
     * The calendar's name as its row shows it.
     *
     * @returns {String} the name
     */
    calendarName() {
      const calendar = this.calendar || {};
      return calendar.name || calendar.title || this.$t('agenda.myCalendar');
    },
    /**
     * The options the suggester searches with: no space scope, so the endpoint
     * suggests the owner's connections and then every other user — the
     * object the attendee drawer passes for a personal calendar, and one the
     * suggester cannot do without.
     *
     * @returns {Object} the search options
     */
    searchOptions() {
      return {currentUser: ''};
    },
    /**
     * @returns {Object} the suggester's labels
     */
    suggesterLabels() {
      return {
        searchPlaceholder: this.$t('agenda.calendarShare.searchPlaceholder'),
        placeholder: this.$t('agenda.calendarShare.searchPlaceholder'),
        noDataLabel: this.$t('agenda.calendarShare.noDataLabel'),
      };
    },
    /**
     * Whom the suggester must not offer: the owner, and the colleagues already
     * shared with.
     *
     * @returns {Array} suggester item keys
     */
    ignoredItems() {
      const items = this.shares.filter(share => share.username).map(share => `organization:${share.username}`);
      items.push(`organization:${eXo.env.portal.userName}`);
      return items;
    },
    /**
     * @returns {String} the title of the open question
     */
    confirmTitle() {
      return this.pendingSharee
        ? this.$t('agenda.calendarShare.confirmMeetingCopies.title')
        : this.$t('agenda.calendarShare.unshareConfirmTitle');
    },
    /**
     * @returns {String} the sentence of the open question — none for the
     *          meeting-copies one, which is a title only
     */
    confirmMessage() {
      if (this.pendingSharee) {
        return '';
      }
      const share = this.toUnshare;
      return share && this.$t('agenda.calendarShare.unshareConfirmMessage', {0: share.displayName || share.username}) || '';
    },
    /**
     * @returns {String} the label of the open question's OK button
     */
    confirmOkLabel() {
      return this.pendingSharee
        ? this.$t('agenda.calendarShare.confirmMeetingCopies.ok')
        : this.$t('agenda.calendarShare.unshare');
    },
  },
  watch: {
    /**
     * Shares the calendar with the colleague the suggester picked, then clears
     * the field for the next one.
     *
     * @param {Object} value the suggester's item, null once cleared
     * @returns {void}
     */
    sharee(value) {
      if (!value) {
        return;
      }
      const username = value.remoteId || value.id && String(value.id).replace(/^organization:/, '');
      this.sharee = null;
      // The suggester's own model is cleared, but the autocomplete under it
      // keeps the picked item as a chip (seen on the rig: its internal value
      // still holds the colleague once the suggester's is null, and
      // deleteCurrentItem drops nothing while no item is keyboard-selected).
      // Its value is emptied directly.
      const autocomplete = this.$refs.shareeSuggester?.$refs?.selectAutoComplete;
      if (autocomplete) {
        autocomplete.internalValue = null;
      }
      if (!username) {
        return;
      }
      if (this.meetingCopies) {
        this.pendingSharee = username;
        this.$refs.confirmDialog.open();
      } else {
        this.share(username);
      }
    },
  },
  created() {
    this.$root.$on('agenda-calendar-share-drawer-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-share-drawer-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer on a calendar and reads its shares.
     *
     * @param {Object} calendar the calendar to share
     * @returns {Promise} resolved once the shares are read
     */
    open(calendar) {
      this.calendar = calendar;
      this.shares = [];
      this.externalShares = [];
      this.error = null;
      this.toUnshare = null;
      this.meetingCopies = false;
      this.pendingSharee = null;
      this.$refs.calendarShareDrawer.open();
      return this.load();
    },
    /**
     * Closes the drawer.
     *
     * @returns {void}
     */
    close() {
      this.$refs.calendarShareDrawer.close();
    },
    /**
     * Forgets the calendar once the drawer is closed.
     *
     * @returns {void}
     */
    forget() {
      this.calendar = null;
      this.sharee = null;
    },
    /**
     * Reads the shares of the calendar, the external ones included.
     *
     * @returns {Promise} resolved once read
     */
    load() {
      this.loading = true;
      return this.$calendarShareService.getShares(this.calendar.id)
        .then(answer => {
          this.shares = answer && answer.shares || [];
          this.externalShares = answer && answer.externalShares || [];
          this.meetingCopies = !!(answer && answer.meetingCopies);
        })
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarShare.loadError'), 'error'))
        .finally(() => this.loading = false);
    },
    /**
     * Shares the calendar with a colleague and lists the answer; a refusal is
     * worded under the field.
     *
     * @param {String} username the colleague
     * @returns {Promise} resolved once shared or refused
     */
    share(username) {
      this.saving = true;
      this.error = null;
      return this.$calendarShareService.share(this.calendar.id, username)
        .then(share => {
          this.shares = this.shares.filter(row => row.shareeIdentityId !== share.shareeIdentityId).concat(share);
          this.externalShares = this.externalShares.filter(row => row.shareeIdentityId !== share.shareeIdentityId);
          this.notifyChanged();
          this.$root.$emit('alert-message', this.$t('agenda.calendarShare.shared', {0: share.displayName || username}), 'success');
        })
        .catch(error => this.error = this.errorLabel(error))
        .finally(() => this.saving = false);
    },
    /**
     * Asks before revoking a share.
     *
     * @param {Object} share the share
     * @returns {void}
     */
    confirmUnshare(share) {
      this.pendingSharee = null;
      this.toUnshare = share;
      this.$refs.confirmDialog.open();
    },
    /**
     * Runs what the owner confirmed: the share of a calendar holding meeting
     * copies, or the revoke.
     *
     * @returns {Promise} the share or the revoke
     */
    confirmed() {
      const username = this.pendingSharee;
      if (username) {
        this.pendingSharee = null;
        return this.share(username);
      }
      return this.unshare();
    },
    /**
     * Forgets the colleague the meeting-copies question was about, once the
     * dialog closes without OK: the field is already clear.
     *
     * @returns {void}
     */
    cancelled() {
      this.pendingSharee = null;
    },
    /**
     * Revokes the share the owner confirmed.
     *
     * @returns {Promise} resolved once revoked
     */
    unshare() {
      const share = this.toUnshare;
      this.toUnshare = null;
      if (!share) {
        return Promise.resolve();
      }
      this.saving = true;
      return this.$calendarShareService.unshare(this.calendar.id, share.shareeIdentityId)
        .then(() => {
          this.shares = this.shares.filter(row => row.shareeIdentityId !== share.shareeIdentityId);
          this.notifyChanged();
          this.$root.$emit('alert-message', this.$t('agenda.calendarShare.unshared', {0: share.displayName || share.username}), 'success');
          // A grant the channel could not withdraw now shows under the external
          // shares, so the list is read again rather than trusted
          return this.load();
        })
        .catch(error => this.$root.$emit('alert-message', this.errorLabel(error), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Removes on the server a share eXo does not record.
     *
     * @param {Object} external the external share
     * @returns {Promise} resolved once removed
     */
    removeExternal(external) {
      this.saving = true;
      return this.$calendarShareService.removeExternalShare(this.calendar.id, external.channelId, external.externalId)
        .then(() => {
          this.externalShares = this.externalShares.filter(row => row !== external);
          this.$root.$emit('alert-message', this.$t('agenda.calendarShare.externalRemoved', {0: this.externalName(external)}), 'success');
        })
        .catch(error => this.$root.$emit('alert-message', this.errorLabel(error), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Tells the lists that a share changed: the row signs read their counts
     * again, on the root for the agenda and on the document for the settings
     * page, which is another Vue app.
     *
     * @returns {void}
     */
    notifyChanged() {
      this.$root.$emit('agenda-calendar-shares-changed');
      document.dispatchEvent(new CustomEvent('agenda-calendar-shares-changed'));
    },
    /**
     * A channel id, worded for the access held outside eXo — the server's
     * host for a CalDAV channel, the id itself for anything else. Only the
     * "outside eXo" rows name their server: delivery is invisible on a
     * colleague's row.
     *
     * @param {String} channelId the channel id, caldav:<serverId>
     * @returns {String} the label
     */
    channelLabel(channelId) {
      if (!channelId) {
        return '';
      }
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      const connector = connectors.find(one => one && typeof one.channelLabel === 'function' && one.channelLabel(channelId));
      return connector && connector.channelLabel(channelId) || channelId.replace(/^caldav:/, 'CalDAV ');
    },
    /**
     * What to call access held outside eXo: the name the channel gives, else
     * the address, else the kind of grantee worded — everyone on the server,
     * a published link.
     *
     * @param {Object} external the access
     * @returns {String} the name
     */
    externalName(external) {
      if (external.displayName) {
        return external.displayName;
      }
      if (external.email) {
        return external.email;
      }
      const key = `agenda.calendarShare.kind.${external.kind}`;
      const label = this.$t(key);
      return label === key ? external.kind || '' : label;
    },
    /**
     * A refusal, worded: the server's message code when the bundle has a
     * sentence for it, the generic error otherwise.
     *
     * @param {Error} error the rejection
     * @returns {String} the sentence
     */
    errorLabel(error) {
      const code = error && error.message;
      if (code && code.startsWith('agenda.share.')) {
        const label = this.$t(code);
        return label === code ? this.$t('agenda.calendarShare.error') : label;
      }
      return this.$t('agenda.calendarShare.error');
    },
  },
};
</script>
