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
    One calendar a space subscribes to: who added it and when, when it was last
    refreshed, a warning when its last refresh failed, and Refresh now / Remove.
    Shared by the drawer of the personal agenda, which removes at once, and the
    space settings drawer, which holds an addition or a removal until its Save:
    a row held so shows what Save will do and an Undo instead of its menu.
  -->
  <v-list-item
    :class="pending && `agenda-space-subscription-pending-${pending}`"
    class="px-0 agenda-space-subscription">
    <v-list-item-content class="py-2">
      <v-list-item-title :title="subscription.name" class="agenda-space-subscription-name">
        <v-icon size="14" class="text-light-color me-2">fas fa-rss</v-icon>
        {{ subscription.name }}
      </v-list-item-title>
      <v-list-item-subtitle v-if="pending" class="agenda-space-subscription-pending">
        {{ pendingLabel }}
      </v-list-item-subtitle>
      <template v-if="pending !== 'add'">
        <v-list-item-subtitle class="agenda-space-subscription-added">
          {{ addedLabel }}
        </v-list-item-subtitle>
        <v-list-item-subtitle class="agenda-space-subscription-refreshed">
          {{ refreshedLabel }}
        </v-list-item-subtitle>
      </template>
      <div
        v-if="subscription.lastError"
        class="warning--text text-wrap mt-1 agenda-space-subscription-warning"
        role="alert">
        <v-icon size="12" class="warning--text me-1">fas fa-exclamation-triangle</v-icon>
        {{ warningLabel(subscription) }}
      </div>
      <div
        v-if="error"
        class="error--text text-wrap mt-1 agenda-space-subscription-error"
        role="alert">
        {{ $t(error) }}
      </div>
    </v-list-item-content>
    <v-list-item-action class="my-0 ms-2 agenda-calendar-actions">
      <v-btn
        v-if="pending"
        :title="$t('agenda.space.settings.subscriptions.undo')"
        :aria-label="$t('agenda.space.settings.subscriptions.undo')"
        :disabled="disabled"
        class="agenda-space-subscription-restore"
        icon
        x-small
        @click="$emit('restore')">
        <v-icon size="14">fa-undo</v-icon>
      </v-btn>
      <v-menu
        v-else
        :value="isRowMenuOpen(subscription.id)"
        content-class="agendaCalendarRowMenu"
        offset-y
        left
        @input="toggleRowMenu(subscription.id, $event)">
        <template #activator="{ on, attrs }">
          <v-btn
            v-bind="attrs"
            :title="$t('agenda.calendar.actions')"
            :disabled="disabled"
            icon
            x-small
            v-on="on">
            <v-icon size="14">fa-ellipsis-v</v-icon>
          </v-btn>
        </template>
        <v-list dense class="pa-0">
          <v-list-item
            :disabled="refreshing"
            class="agenda-space-subscription-refresh"
            @click="refresh">
            <v-list-item-title>{{ $t('agenda.calendarSubscription.refresh') }}</v-list-item-title>
          </v-list-item>
          <v-list-item class="agenda-space-subscription-remove" @click="$emit('remove')">
            <v-list-item-title class="error--text">{{ $t('agenda.calendarSubscription.remove') }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';
import {WITHDRAWN_CODE, errorMessageKey} from '../../js/CalendarSubscriptionService.js';

export default {
  mixins: [calendarRowMenuMixin],
  props: {
    subscription: {
      type: Object,
      required: true,
    },
    // 'add' or 'remove' while the space settings drawer holds the change until its Save
    pending: {
      type: String,
      default: null,
    },
    // While the space settings drawer saves: no change can be made to the row
    disabled: {
      type: Boolean,
      default: false,
    },
    // The message key of the last failure to save this row's change
    error: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    refreshing: false,
  }),
  computed: {
    /**
     * @returns {String} what Save will do with the row
     */
    pendingLabel() {
      return this.pending === 'add'
        ? this.$t('agenda.space.settings.subscriptions.toAdd')
        : this.$t('agenda.space.settings.subscriptions.toRemove');
    },
    /**
     * @returns {String} who added it and when
     */
    addedLabel() {
      return this.$t('agenda.calendarSubscription.addedBy', {
        0: this.subscription.creatorFullName || this.subscription.creatorUsername || '',
        1: this.subscription.createdDate ? this.formatDate(this.subscription.createdDate, false) : '',
      });
    },
    /**
     * @returns {String} when its feed was last imported, or that it never was
     */
    refreshedLabel() {
      return this.subscription.lastSuccessDate
        ? this.$t('agenda.calendarSubscription.lastRefresh', {0: this.formatDate(this.subscription.lastSuccessDate, true)})
        : this.$t('agenda.calendarSubscription.neverRefreshed');
    },
  },
  methods: {
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
     * The warning of a subscription whose last refresh failed, in the words
     * of the one thing the two cases do not share: what becomes of the events
     * already imported (EXO-90402).
     *
     * `lastError` being {@link WITHDRAWN_CODE} is a link of this eXo that opens
     * nothing: only the in-process branch of the refresh raises it, from this
     * platform's own database, so it is eXo's own certain word that the
     * publisher withdrew the link, and the only failure on which the imported
     * events are removed, a week after the last refresh
     * (`AgendaCalendarSubscriptionServiceImpl#isWithdrawnForLong`). Any other
     * failure is retried for ever and purges nothing, so the warning says since
     * when nothing has been imported and that the events stay as they are. A
     * withdrawn link that never imported anything has nothing to remove, which
     * the cleanup guards on (`refreshClaimed`, `getContentHash() != null`), so
     * no cleanup is promised to it.
     *
     * None of the three claims the events are on screen now: the row keeps its
     * failure for good after the purge, so a present-tense sentence would turn
     * false at an hour no field of the row can tell. The date is the last
     * successful refresh, the one the row's "Last refresh" line shows, and a
     * success may import nothing (a 304, an unchanged digest), hence "imported".
     *
     * @param {Object} subscription a row
     * @returns {String} the warning
     */
    warningLabel(subscription) {
      if (subscription.lastError === WITHDRAWN_CODE) {
        return subscription.lastSuccessDate
          ? this.$t('agenda.calendarSubscription.withdrawn')
          : this.$t('agenda.calendarSubscription.withdrawnNeverImported');
      }
      const reason = this.$t(errorMessageKey(subscription.lastError));
      return subscription.lastSuccessDate
        ? this.$t('agenda.calendarSubscription.stillImported', {0: reason, 1: this.formatDate(subscription.lastSuccessDate, true)})
        : this.$t('agenda.calendarSubscription.neverImported', {0: reason});
    },
    /**
     * Refreshes the subscription now, whatever its list holds until a Save: the
     * answer, a failure of the link included, goes to the list as `refreshed`,
     * and the agenda reads the events again.
     *
     * @returns {Promise} resolved once shown
     */
    refresh() {
      if (this.refreshing) {
        return Promise.resolve();
      }
      this.refreshing = true;
      return this.$calendarSubscriptionService.refreshSubscription(this.subscription.id)
        .then(refreshed => {
          if (refreshed) {
            this.$emit('refreshed', refreshed);
          }
          if (refreshed && refreshed.lastError) {
            this.$root.$emit('alert-message', this.warningLabel(refreshed), 'warning');
          } else {
            this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.refreshed', {0: this.subscription.name}), 'success');
          }
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'))
        .finally(() => this.refreshing = false);
    },
  },
};
</script>
