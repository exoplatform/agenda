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
    The calendars the user subscribed to by link (EXO-90278): one row each, a
    visibility checkbox, the reason the last refresh failed when it did, and a
    menu to edit, refresh or unsubscribe. The section owns its root and draws
    nothing while the user has no subscription, the same rule the remote
    sections follow. The drawer lives in Agenda.vue, never here.
  -->
  <section
    v-if="subscriptions.length"
    class="agenda-left-panel-section d-flex flex-column mb-5 agenda-subscribed-calendars">
    <div class="agenda-left-panel-title text-sub-title">
      <span class="flex-grow-1">{{ $t('agenda.calendarSubscription.section') }}</span>
    </div>
    <div class="agenda-left-panel-calendars">
      <v-list class="pa-0" dense>
        <v-list-item
          v-for="subscription in subscriptions"
          :key="subscription.id"
          class="agenda-calendar-settings px-0 agenda-subscribed-calendar">
          <v-list-item-content :title="subscription.name" class="flex-grow-1 pa-0">
            <v-checkbox
              :input-value="isDisplayed(subscription)"
              :color="subscription.color"
              :label="subscription.name"
              class="agenda-calendar-settings-color ms-4"
              dense
              hide-details
              @change="toggle(subscription)" />
          </v-list-item-content>
          <!--
            A subscription whose last refresh failed looks exactly like one that
            works — its events are the last good copy — so the row says so.
          -->
          <v-list-item-action
            v-if="subscription.lastError"
            class="my-0 ms-2 flex-grow-0 justify-center">
            <span
              :title="errorTooltip(subscription)"
              :aria-label="errorTooltip(subscription)"
              class="d-flex agenda-calendar-subscription-error-sign"
              role="img">
              <v-icon size="14" class="warning--text">fas fa-exclamation-triangle</v-icon>
            </span>
          </v-list-item-action>
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
                <v-list-item class="agenda-calendar-subscription-edit" @click="edit(subscription)">
                  <v-list-item-title>{{ $t('agenda.calendarSubscription.edit') }}</v-list-item-title>
                </v-list-item>
                <v-list-item
                  :disabled="refreshingIds.includes(subscription.id)"
                  class="agenda-calendar-subscription-refresh"
                  @click="refresh(subscription)">
                  <v-list-item-title>{{ $t('agenda.calendarSubscription.refresh') }}</v-list-item-title>
                </v-list-item>
                <v-list-item class="agenda-calendar-subscription-unsubscribe" @click="confirmUnsubscribe(subscription)">
                  <v-list-item-title class="error--text">{{ $t('agenda.calendarSubscription.unsubscribe') }}</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </v-list-item-action>
          <!--
            A subscribed calendar is read-only and filled from outside: the sign
            says so, at the end of the row, as the published sign does.
          -->
          <v-list-item-action class="my-0 ms-2 flex-grow-0 justify-center">
            <span
              :title="$t('agenda.calendarSubscription.subscriptionTooltip')"
              :aria-label="$t('agenda.calendarSubscription.subscriptionTooltip')"
              class="d-flex agenda-calendar-subscription-sign"
              role="img">
              <v-icon size="14" class="text-light-color">fas fa-rss</v-icon>
            </span>
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </div>
    <exo-confirm-dialog
      ref="unsubscribeConfirmDialog"
      :title="$t('agenda.calendarSubscription.unsubscribeConfirmTitle')"
      :message="unsubscribeMessage"
      :ok-label="$t('agenda.calendarSubscription.unsubscribe')"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="unsubscribe" />
  </section>
</template>

<script>
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';
import {errorMessageKey} from '../../js/CalendarSubscriptionService.js';

export default {
  mixins: [calendarRowMenuMixin],
  data: () => ({
    subscriptions: [],
    hiddenIds: [],
    refreshingIds: [],
    toUnsubscribe: null,
  }),
  computed: {
    /**
     * @returns {String} the browser storage key of the hidden subscribed
     *          calendars, per user
     */
    storageKey() {
      return `agenda.hiddenSubscribedCalendars.${eXo.env.portal.userIdentityId}`;
    },
    /**
     * @returns {String} the confirmation sentence
     */
    unsubscribeMessage() {
      return this.toUnsubscribe && this.$t('agenda.calendarSubscription.unsubscribeConfirmMessage', {0: this.toUnsubscribe.name}) || '';
    },
  },
  created() {
    this.hiddenIds = this.readHiddenIds();
    this.$root.$on('agenda-refresh-subscribed-calendars', this.retrieve);
    this.retrieve();
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-subscribed-calendars', this.retrieve);
  },
  methods: {
    /**
     * Reads the user's subscriptions.
     *
     * @returns {Promise} resolved once listed
     */
    retrieve() {
      return this.$calendarSubscriptionService.getSubscriptions()
        .then(subscriptions => this.subscriptions = subscriptions || [])
        .catch(() => this.subscriptions = []);
    },
    /**
     * @param {Object} subscription a row
     * @returns {Boolean} whether its events are shown
     */
    isDisplayed(subscription) {
      return !this.hiddenIds.includes(Number(subscription.calendarId));
    },
    /**
     * Shows or hides a subscribed calendar's events, remembers it for this user
     * and tells the agenda.
     *
     * @param {Object} subscription the row toggled
     * @returns {void}
     */
    toggle(subscription) {
      const calendarId = Number(subscription.calendarId);
      this.hiddenIds = this.hiddenIds.includes(calendarId)
        ? this.hiddenIds.filter(id => id !== calendarId)
        : this.hiddenIds.concat(calendarId);
      this.writeHiddenIds();
      this.$root.$emit('agenda-subscribed-calendars-visibility-changed', this.hiddenIds.slice());
    },
    /**
     * @returns {Array} the hidden calendar ids stored for this user
     */
    readHiddenIds() {
      try {
        const stored = JSON.parse(localStorage.getItem(this.storageKey) || '[]');
        return Array.isArray(stored) ? stored.map(Number) : [];
      } catch (e) {
        return [];
      }
    },
    /**
     * Stores the hidden calendar ids for this user.
     *
     * @returns {void}
     */
    writeHiddenIds() {
      try {
        localStorage.setItem(this.storageKey, JSON.stringify(this.hiddenIds));
      } catch (e) {
        // A browser refusing storage keeps the choice for this page only
      }
    },
    /**
     * @param {Object} subscription a row
     * @returns {String} what the warning sign says
     */
    errorTooltip(subscription) {
      return this.$t('agenda.calendarSubscription.lastErrorTooltip', {0: this.$t(errorMessageKey(subscription.lastError))});
    },
    /**
     * Opens the drawer on a subscription.
     *
     * @param {Object} subscription the row
     * @returns {void}
     */
    edit(subscription) {
      this.$root.$emit('agenda-calendar-subscription-drawer-open', subscription);
    },
    /**
     * Refreshes a subscription now; the row takes the answer, a failure of the
     * link included, and the grid reads the events again.
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
            this.$root.$emit('alert-message', this.$t(errorMessageKey(refreshed.lastError)), 'warning');
          } else {
            this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.refreshed', {0: subscription.name}), 'success');
          }
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'))
        .finally(() => this.refreshingIds = this.refreshingIds.filter(id => id !== subscription.id));
    },
    /**
     * Asks before unsubscribing.
     *
     * @param {Object} subscription the row
     * @returns {void}
     */
    confirmUnsubscribe(subscription) {
      this.toUnsubscribe = subscription;
      this.$refs.unsubscribeConfirmDialog.open();
    },
    /**
     * Unsubscribes the confirmed subscription: its row goes, its visibility is
     * forgotten, and the grid reads the events again.
     *
     * @returns {Promise} resolved once done
     */
    unsubscribe() {
      const subscription = this.toUnsubscribe;
      if (!subscription) {
        return Promise.resolve();
      }
      return this.$calendarSubscriptionService.deleteSubscription(subscription.id)
        .then(() => {
          this.subscriptions = this.subscriptions.filter(row => row.id !== subscription.id);
          if (!this.isDisplayed(subscription)) {
            this.toggle(subscription);
          }
          this.toUnsubscribe = null;
          this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.unsubscribed', {0: subscription.name}), 'success');
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'));
    },
  },
};
</script>
