/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation,
 */

<template>
  <v-app>
    <!--
      One line for the application, as the other applications of this page draw
      theirs (forms' FormsSpaceSettings): its name, a summary, and one edit icon
      opening the drawer that holds every agenda setting of the space.
    -->
    <v-card
      id="agendaSpaceSetting"
      class="card-border-radius"
      flat>
      <v-list class="pa-0" dense>
        <v-list-item class="pa-0">
          <v-list-item-content>
            <v-list-item-title class="text-title py-1">
              {{ $t('agenda.space.settings.title') }}
            </v-list-item-title>
            <v-list-item-subtitle class="d-flex align-center pt-0 pb-1 text-wrap agenda-space-settings-summary">
              {{ $t('agenda.space.settings.eventColor') }}
              <span
                :title="calendarColor"
                :aria-label="$t('agenda.space.settings.eventColor.current', {0: calendarColor || ''})"
                :style="swatchStyle(calendarColor, 12)"
                class="d-inline-flex rounded-circle mx-1 agenda-space-settings-summary-color"
                role="img"></span>
              <span v-if="canManageSubscriptions" class="agenda-space-subscriptions-count">
                · {{ $t('agenda.space.settings.subscriptions.summary', {0: subscriptionsCount}) }}
              </span>
            </v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <!--
              The edit action of this settings page, as its other rows draw it
              (social's SpaceSettingAccess, SpaceSettingCategories): a small icon
              button, fa-edit at 18 in the default icon colour.
            -->
            <v-btn
              id="agendaSpaceSettingsOpen"
              :title="$t('agenda.space.settings.edit')"
              :aria-label="$t('agenda.space.settings.edit')"
              :disabled="!calendar"
              small
              icon
              @click="$refs.drawer.open()">
              <v-icon size="18" class="icon-default-color">fa-edit</v-icon>
            </v-btn>
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </v-card>
    <agenda-space-settings-drawer
      ref="drawer"
      :calendar="calendar"
      :can-manage-subscriptions="canManageSubscriptions"
      :space-name="spaceName"
      @color-saved="calendar.color = $event" />
    <!-- The drawer adding a calendar link, opened by the drawer above: the agenda application is not on this page -->
    <agenda-calendar-subscription-drawer v-if="canManageSubscriptions" />
  </v-app>
</template>
<script>
import {swatchStyle} from '../js/SpaceSettingsColor.js';

export default {
  data: () => ({
    calendar: null,
    currentSpace: null,
    subscriptionsCount: 0,
  }),
  computed: {
    /**
     * @returns {String} the colour the space's events take, as stored
     */
    calendarColor() {
      return this.calendar && this.calendar.color || null;
    },
    /**
     * @returns {String} the name of the space, as its settings drawers write it
     */
    spaceName() {
      const space = this.currentSpace;
      return space && (space.displayName || space.prettyName) || '';
    },
    /**
     * @returns {Number} the identity id of the space, owner of its calendar
     */
    calendarOwnerId() {
      return Number(this.calendar?.owner?.id);
    },
    /**
     * Whether the user is a real manager of the space, the server's own answer
     * for publishing its calendar, which is also who manages the calendars the
     * space subscribes to.
     *
     * @returns {Boolean} true for a real manager
     */
    canManageSubscriptions() {
      return !!(this.calendar && this.calendar.acl && this.calendar.acl.canPublish && this.calendarOwnerId);
    },
  },
  watch: {
    /**
     * Counts the space's subscriptions once the user is known to manage them.
     *
     * @param {Boolean} canManage whether the user manages them
     * @returns {void}
     */
    canManageSubscriptions(canManage) {
      if (canManage) {
        this.countSubscriptions();
      }
    },
  },
  created() {
    this.$root.$on('agenda-space-subscriptions-changed', this.countSubscriptions);
    this.getCalendar();
  },
  beforeDestroy() {
    this.$root.$off('agenda-space-subscriptions-changed', this.countSubscriptions);
  },
  methods: {
    swatchStyle,
    /**
     * Counts the calendars the space subscribes to; a failure leaves the count
     * as it was.
     *
     * @returns {Promise} resolved once counted
     */
    countSubscriptions() {
      if (!this.canManageSubscriptions || !this.$calendarSubscriptionService) {
        return Promise.resolve();
      }
      return this.$calendarSubscriptionService.getSubscriptions(this.calendarOwnerId)
        .then(subscriptions => this.subscriptionsCount = subscriptions && subscriptions.length || 0)
        .catch(() => null);
    },
    /**
     * Reads the space's calendar, the one whose colour the settings show.
     *
     * @returns {void}
     */
    getCalendar() {
      if (eXo.env.portal.spaceId) {
        const spaceId = eXo.env.portal.spaceId;
        this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              const ownerIds = [space.identity.id];
              return this.$calendarService.getCalendars(0, 1, false, ownerIds);
            }
          })
          .then(data => {
            this.calendar = data && data.calendars && data.calendars.length && data.calendars[0] || null;
          });
      }
    },
  },
};
</script>