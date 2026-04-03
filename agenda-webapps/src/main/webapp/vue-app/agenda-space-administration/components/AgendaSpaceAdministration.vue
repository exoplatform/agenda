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
        <v-list class="pa-0">
          <v-list-item class="pa-0">
            <v-list-item-content>
              <v-list-item-title class="text-title">
                {{ $t('agenda.space.settings.title') }}
              </v-list-item-title>
              <v-list-item-title class="pt-2">
                {{ $t('agenda.space.settings.eventColor') }}
              </v-list-item-title>
            </v-list-item-content>
            <v-list-item-action class="mb-0">
              <v-list-item-action
                :id="calendarColourMenuId"
                class="me-1 mb-0">
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
        </v-list>
      </v-card>
    </template>
  </v-app>
</template>
<script>

export default {
  data: () => ({
    calendarColor: null,
    newCalendarColor: null,
    calendar: {},
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
  },
  created() {
    this.getCalendar();
    $(document).on('click', (e) => {
      if (e.target && !$(e.target).parents(`.${this.calendarColourMenuId}`).length) {
        this.newCalendarColor = this.calendarColor;
        this.menu = false;
      }
    });
  },
  methods: {    
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