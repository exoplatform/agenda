<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.
-->
<template>
  <!--
    The calendars a space subscribes to (EXO-90373), for its managers: add one by
    link, and for each who added it and when, when it was last refreshed, a
    warning when its last refresh failed, and Refresh now / Remove. Opened by a
    root event from the space calendar's menu in the personal agenda and from the
    space settings; mounted once on each page, never in a row. The server refuses
    anyone but a real manager of the space; the openers are only drawn for them.
  -->
  <exo-drawer
    ref="spaceSubscriptionsDrawer"
    :loading="loading"
    class="agendaSpaceSubscriptionsDrawer"
    right>
    <template slot="title">
      {{ $t('agenda.calendarSubscription.spaceDrawerTitle') }}
    </template>
    <template slot="content">
      <div class="mx-4 mt-4">
        <p class="text-light-color agenda-space-subscriptions-explanation">
          {{ $t('agenda.calendarSubscription.spaceExplanation', {0: spaceName}) }}
        </p>
        <v-btn
          class="btn btn-primary mb-4 agenda-space-subscriptions-add"
          @click="add">
          <v-icon size="14" class="me-2">fas fa-plus</v-icon>
          {{ $t('agenda.calendarSubscription.add') }}
        </v-btn>
        <p
          v-if="loaded && !subscriptions.length"
          class="text-light-color agenda-space-subscriptions-empty">
          {{ $t('agenda.calendarSubscription.spaceEmpty') }}
        </p>
        <p
          v-if="loadError"
          class="error--text agenda-space-subscriptions-load-error"
          role="alert">
          {{ $t(loadError) }}
        </p>
        <v-list class="pa-0" dense>
          <v-list-item
            v-for="subscription in subscriptions"
            :key="subscription.id"
            class="px-0 agenda-space-subscription">
            <v-list-item-content class="py-2">
              <v-list-item-title :title="subscription.name" class="agenda-space-subscription-name">
                <v-icon size="14" class="text-light-color me-2">fas fa-rss</v-icon>
                {{ subscription.name }}
              </v-list-item-title>
              <v-list-item-subtitle class="agenda-space-subscription-added">
                {{ addedLabel(subscription) }}
              </v-list-item-subtitle>
              <v-list-item-subtitle class="agenda-space-subscription-refreshed">
                {{ refreshedLabel(subscription) }}
              </v-list-item-subtitle>
              <div
                v-if="subscription.lastError"
                class="warning--text text-wrap mt-1 agenda-space-subscription-warning"
                role="alert">
                <v-icon size="12" class="warning--text me-1">fas fa-exclamation-triangle</v-icon>
                {{ warningLabel(subscription) }}
              </div>
            </v-list-item-content>
            <v-list-item-action class="my-0 ms-2 agenda-calendar-actions">
              <v-menu
                :value="isRowMenuOpen(subscription.id)"
                content-class="agendaCalendarRowMenu"
                offset-y
                left
                @input="toggleRowMenu(subscription.id, $event)">
                <template #activator="{ on, attrs }">
                  <v-btn
                    v-bind="attrs"
                    :title="$t('agenda.calendar.actions')"
                    icon
                    x-small
                    v-on="on">
                    <v-icon size="14">fa-ellipsis-v</v-icon>
                  </v-btn>
                </template>
                <v-list dense class="pa-0">
                  <v-list-item
                    :disabled="refreshingIds.includes(subscription.id)"
                    class="agenda-space-subscription-refresh"
                    @click="refresh(subscription)">
                    <v-list-item-title>{{ $t('agenda.calendarSubscription.refresh') }}</v-list-item-title>
                  </v-list-item>
                  <v-list-item class="agenda-space-subscription-remove" @click="confirmRemove(subscription)">
                    <v-list-item-title class="error--text">{{ $t('agenda.calendarSubscription.remove') }}</v-list-item-title>
                  </v-list-item>
                </v-list>
              </v-menu>
            </v-list-item-action>
          </v-list-item>
        </v-list>
      </div>
      <exo-confirm-dialog
        ref="removeConfirmDialog"
        :title="$t('agenda.calendarSubscription.removeConfirmTitle')"
        :message="removeMessage"
        :ok-label="$t('agenda.calendarSubscription.remove')"
        :cancel-label="$t('agenda.button.cancel')"
        @ok="remove" />
    </template>
  </exo-drawer>
</template>

<script>
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';
import {WITHDRAWN_CODE, errorMessageKey} from '../../js/CalendarSubscriptionService.js';

export default {
  mixins: [calendarRowMenuMixin],
  data: () => ({
    ownerId: null,
    spaceName: '',
    subscriptions: [],
    loading: false,
    loaded: false,
    loadError: null,
    refreshingIds: [],
    toRemove: null,
  }),
  computed: {
    /**
     * @returns {String} the confirmation sentence of a removal
     */
    removeMessage() {
      return this.toRemove && this.$t('agenda.calendarSubscription.removeConfirmMessage', {0: this.toRemove.name, 1: this.spaceName}) || '';
    },
  },
  created() {
    this.$root.$on('agenda-space-subscriptions-drawer-open', this.open);
    this.$root.$on('agenda-space-subscriptions-changed', this.changed);
  },
  beforeDestroy() {
    this.$root.$off('agenda-space-subscriptions-drawer-open', this.open);
    this.$root.$off('agenda-space-subscriptions-changed', this.changed);
  },
  methods: {
    /**
     * Opens the drawer on a space and lists its subscriptions.
     *
     * @param {Object} space {ownerId, spaceName}: the space's identity id and
     *          display name
     * @returns {Promise} resolved once listed
     */
    open(space) {
      this.ownerId = space && space.ownerId || null;
      this.spaceName = space && space.spaceName || '';
      this.subscriptions = [];
      this.loaded = false;
      this.loadError = null;
      this.toRemove = null;
      this.$refs.spaceSubscriptionsDrawer.open();
      return this.retrieve();
    },
    /**
     * Reads the space's subscriptions again, once a calendar was added to it.
     *
     * @param {Number} ownerId the space whose subscriptions changed
     * @returns {Promise} resolved once listed
     */
    changed(ownerId) {
      return ownerId && Number(ownerId) === Number(this.ownerId) ? this.retrieve() : Promise.resolve();
    },
    /**
     * Lists the space's subscriptions; a refusal is shown in place.
     *
     * @returns {Promise} resolved once listed
     */
    retrieve() {
      if (!this.ownerId) {
        return Promise.resolve();
      }
      this.loading = true;
      return this.$calendarSubscriptionService.getSubscriptions(this.ownerId)
        .then(subscriptions => {
          this.subscriptions = subscriptions || [];
          this.loadError = null;
        })
        .catch(error => {
          this.subscriptions = [];
          this.loadError = errorMessageKey(error && error.message);
        })
        .finally(() => {
          this.loading = false;
          this.loaded = true;
        });
    },
    /**
     * Opens the drawer adding a calendar link, for this space.
     *
     * @returns {void}
     */
    add() {
      this.$root.$emit('agenda-calendar-subscription-drawer-open', null, {ownerId: this.ownerId, spaceName: this.spaceName});
    },
    /**
     * A date as the user reads it.
     *
     * @param {Number} time epoch milliseconds
     * @param {Boolean} withTime whether the hour is given too
     * @returns {String} the date
     */
    formatDate(time, withTime) {
      const lang = window.eXo && eXo.env && eXo.env.portal && eXo.env.portal.language || 'en';
      const options = withTime
        ? {year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'}
        : {year: 'numeric', month: 'short', day: 'numeric'};
      return new Date(time).toLocaleString(lang, options);
    },
    /**
     * @param {Object} subscription a row
     * @returns {String} who added it and when
     */
    addedLabel(subscription) {
      return this.$t('agenda.calendarSubscription.addedBy', {
        0: subscription.creatorFullName || subscription.creatorUsername || '',
        1: subscription.createdDate ? this.formatDate(subscription.createdDate, false) : '',
      });
    },
    /**
     * @param {Object} subscription a row
     * @returns {String} when its feed was last imported, or that it never was
     */
    refreshedLabel(subscription) {
      return subscription.lastSuccessDate
        ? this.$t('agenda.calendarSubscription.lastRefresh', {0: this.formatDate(subscription.lastSuccessDate, true)})
        : this.$t('agenda.calendarSubscription.neverRefreshed');
    },
    /**
     * The warning of a subscription whose last refresh failed: a link its
     * publisher withdrew says so, and that members still see the last copy;
     * any other failure gives its reason.
     *
     * @param {Object} subscription a row
     * @returns {String} the warning
     */
    warningLabel(subscription) {
      if (subscription.lastError === WITHDRAWN_CODE) {
        return this.$t('agenda.calendarSubscription.withdrawn');
      }
      return this.$t('agenda.calendarSubscription.lastErrorTooltip', {0: this.$t(errorMessageKey(subscription.lastError))});
    },
    /**
     * Refreshes a subscription now; the row takes the answer, a failure of the
     * link included, and the agenda reads the events again.
     *
     * @param {Object} subscription the row
     * @returns {Promise} resolved once shown
     */
    refresh(subscription) {
      if (this.refreshingIds.includes(subscription.id)) {
        return Promise.resolve();
      }
      this.refreshingIds = this.refreshingIds.concat(subscription.id);
      return this.$calendarSubscriptionService.refreshSubscription(subscription.id)
        .then(refreshed => {
          this.subscriptions = this.subscriptions.map(row => row.id === subscription.id && refreshed || row);
          if (refreshed && refreshed.lastError) {
            this.$root.$emit('alert-message', this.warningLabel(refreshed), 'warning');
          } else {
            this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.refreshed', {0: subscription.name}), 'success');
          }
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'))
        .finally(() => this.refreshingIds = this.refreshingIds.filter(id => id !== subscription.id));
    },
    /**
     * Asks before removing.
     *
     * @param {Object} subscription the row
     * @returns {void}
     */
    confirmRemove(subscription) {
      this.toRemove = subscription;
      this.$refs.removeConfirmDialog.open();
    },
    /**
     * Removes the confirmed subscription from the space: its row goes and the
     * agenda reads the events again.
     *
     * @returns {Promise} resolved once done
     */
    remove() {
      const subscription = this.toRemove;
      if (!subscription) {
        return Promise.resolve();
      }
      return this.$calendarSubscriptionService.deleteSubscription(subscription.id)
        .then(() => {
          this.subscriptions = this.subscriptions.filter(row => row.id !== subscription.id);
          this.toRemove = null;
          this.$root.$emit('agenda-space-subscriptions-removed', this.ownerId);
          this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.removed', {0: subscription.name, 1: this.spaceName}), 'success');
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'));
    },
  },
};
</script>
