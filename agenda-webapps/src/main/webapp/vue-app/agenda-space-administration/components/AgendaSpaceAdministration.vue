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
    <template>
      <v-card
        id="agendaSpaceSetting"
        class="card-border-radius"
        flat>
        <div class="text-title">
          {{ $t('agenda.space.settings.title') }}
        </div>
        <v-list class="pa-0">
          <v-list-item class="pa-0 mt-n2">
            <v-list-item-content>
              <v-list-item-title>
                {{ $t('agenda.space.settings.eventColor') }}
              </v-list-item-title>
            </v-list-item-content>
            <v-list-item-action>
              <v-list-item-action
                :id="calendarColourMenuId"
                class="me-1 my-auto">
                <v-menu
                  ref="menu"
                  v-model="menu"
                  :close-on-content-click="false"
                  :content-class="calendarColourMenuId"
                  bottom
                  left>
                  <template #activator="{ on, attrs }">
                    <v-card
                      :color="calendarColor"
                      height="30"
                      width="30"
                      v-bind="attrs"
                      v-on="on" />
                    <div class="my-auto ms-2">{{ calendarColor }}</div>
                  </template>
                  <v-card>
                    <v-color-picker
                      v-model="newCalendarColor"
                      class="ma-2"
                      :swatches="swatches"
                      mode="hexa"
                      show-swatches 
                      flat />
                    <v-card-actions>
                      <v-spacer />
                      <v-btn
                        :disabled="saving"
                        class="btn ms-2"
                        @click="closeMenu">
                        {{ $t('agenda.button.cancel') }}
                      </v-btn>
                      <v-btn
                        :loading="saving"
                        :disabled="saving"
                        class="btn btn-primary ms-2"
                        @click="applyColor">
                        {{ $t('agenda.button.apply') }}
                      </v-btn>
                    </v-card-actions>
                  </v-card>
                </v-menu>
              </v-list-item-action>
            </v-list-item-action>
          </v-list-item>
          <!--
            The calendars the space subscribes to (EXO-90373), next to the colour
            their events take: for a real manager of the space only, the rule the
            server applies to them.
          -->
          <v-list-item
            v-if="canManageSubscriptions"
            class="pa-0 agenda-space-subscriptions-setting">
            <v-list-item-content>
              <v-list-item-title>
                {{ $t('agenda.space.settings.subscriptions.title') }}
              </v-list-item-title>
              <v-list-item-subtitle class="agenda-space-subscriptions-count">
                {{ $t('agenda.space.settings.subscriptions.count', {0: subscriptionsCount}) }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <v-list-item-action>
              <!--
                The edit action of this settings page, as its other rows draw it
                (social's SpaceSettingAccess, SpaceSettingCategories,
                SpaceSettingPublicSite, SpaceSettingSubspaces): a small icon
                button, fa-edit at 18 in the default icon colour, its tooltip
                naming what it edits.
              -->
              <v-btn
                :title="$t('agenda.space.settings.subscriptions.button.tooltip')"
                small
                icon
                class="agenda-space-subscriptions-manage"
                @click="openSubscriptions">
                <v-icon size="18" class="icon-default-color">fa-edit</v-icon>
              </v-btn>
            </v-list-item-action>
          </v-list-item>
        </v-list>
      </v-card>
    </template>
    <!--
      The agenda application is not on this page: the drawers it would host are
      mounted here, once, and opened by the same root events.
    -->
    <agenda-space-subscriptions-drawer v-if="canManageSubscriptions" />
    <agenda-calendar-subscription-drawer v-if="canManageSubscriptions" />
  </v-app>
</template>
<script>

export default {
  data: () => ({
    calendarColor: null,
    newCalendarColor: null,
    calendar: {},
    currentSpace: null,
    subscriptionsCount: 0,
    saving: false,
    menu: false,
    swatches: [
      ['#FF0000', '#319ab3', '#f97575'],
      ['#98cc81', '#4273c8', '#cea6ac'],
      ['#bc99e7', '#9ee4f5', '#774ea9'],
      ['#ffa500', '#bed67e', '#0E100F'],
      ['#ffaacc', '#0000AA', '#000055'],
    ],
  }),
  computed: {
    calendarOwnerId() {
      return Number(this.calendar?.owner?.id);
    },
    calendarColourMenuId() {
      return `settingsMenu${this.calendarOwnerId}`;
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
    this.$root.$on('agenda-space-subscriptions-removed', this.countSubscriptions);
    this.getCalendar();
    $(document).on('click', (e) => {
      if (e.target && !$(e.target).parents(`.${this.calendarColourMenuId}`).length) {
        this.newCalendarColor = this.calendarColor;
        this.menu = false;
      }
    });
  },
  beforeDestroy() {
    this.$root.$off('agenda-space-subscriptions-changed', this.countSubscriptions);
    this.$root.$off('agenda-space-subscriptions-removed', this.countSubscriptions);
  },
  methods: {
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
     * Opens the drawer of the calendars the space subscribes to.
     *
     * @returns {void}
     */
    openSubscriptions() {
      const space = this.currentSpace;
      this.$root.$emit('agenda-space-subscriptions-drawer-open', {
        ownerId: this.calendarOwnerId,
        spaceName: space && (space.displayName || space.prettyName) || '',
      });
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
            this.calendarColor = this.calendar.color;
            this.newCalendarColor = this.calendar.color;
          });
      }
    },
    applyColor() {
      const calendarToSave = JSON.parse(JSON.stringify(this.calendar));
      calendarToSave.color = this.newCalendarColor;
      this.saving = true;
      this.$calendarService.saveCalendar(calendarToSave)
        .then(() => {
          this.calendar.color = this.newCalendarColor;
          this.calendarColor = this.newCalendarColor;
          this.closeMenu();
        })
        .finally(() => this.saving = false);
    },
    closeMenu() {
      this.newCalendarColor = this.calendarColor;
      this.menu = false;
    },
  },
};
</script>