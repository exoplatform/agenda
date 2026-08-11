<!--

    This file is part of the Meeds project (https://meeds.io/).

    Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program; if not, write to the Free Software Foundation,
    Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

-->
<template>
  <v-menu
    ref="menu"
    v-model="menu"
    :close-on-content-click="false"
    :content-class="eventColorMenuId"
    :disabled="!hasCalendarOwner"
    bottom
    left>
    <template #activator="{ on, attrs }">
      <div
        v-if="hasCalendarOwner"
        :id="eventColorMenuId"
        class="d-flex align-center event-color-activator"
        v-bind="attrs"
        v-on="on">
        <v-card
          :color="eventColor"
          height="20"
          width="20"
          flat
          class="event-color-swatch" />
        <div class="ms-2">{{ eventColor }}</div>
      </div>
      <div v-else class="text-light-color">
        {{ $t('agenda.label.selectSpaceToChooseColor') }}
      </div>
    </template>
    <v-card>
      <v-color-picker
        v-model="newEventColor"
        :swatches="$agendaUtils.EVENT_COLOR_SWATCHES"
        class="ma-2"
        mode="hexa"
        show-swatches
        flat />
      <v-card-actions>
        <v-spacer />
        <v-btn class="btn ms-2" @click="closeMenu">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn class="btn btn-primary ms-2" @click="applyColor">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-menu>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    calendars: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    menu: false,
    newEventColor: null,
    calendarColor: null,
    calendarColorResolved: false,
  }),
  computed: {
    defaultEventColor() {
      return this.$agendaUtils.EVENT_COLOR_SWATCHES[0][0];
    },
    // The default color of the palette is displayed only once it's known that no
    // calendar color has to be displayed, else it would be displayed first and
    // replaced afterwards by the effective color
    eventColor() {
      return this.event.color
        || (this.event.calendar && this.event.calendar.color)
        || this.calendarColor
        || (this.calendarColorResolved && this.defaultEventColor)
        || null;
    },
    calendarOwner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    // No color can be inherited nor chosen until the calendar owner is selected
    hasCalendarOwner() {
      const owner = this.calendarOwner;
      return !!owner && (!!Number(owner.id) || !!(owner.remoteId && owner.providerId));
    },
    // Calendar already retrieved by the parent component, if any
    ownerCalendar() {
      const owner = this.calendarOwner;
      if (!owner || !owner.remoteId || !owner.providerId) {
        return null;
      }
      return this.calendars.find(calendar => calendar
                                             && calendar.owner
                                             && calendar.owner.remoteId === owner.remoteId
                                             && calendar.owner.providerId === owner.providerId) || null;
    },
    eventColorMenuId() {
      return `eventColorMenu${this._uid}`;
    },
  },
  watch: {
    menu(opened) {
      if (opened) {
        this.newEventColor = this.eventColor || this.defaultEventColor;
      }
    },
    calendarOwner: {
      immediate: true,
      handler() {
        this.retrieveCalendarColor();
      },
    },
  },
  methods: {
    // When creating an event, the calendar isn't retrieved with the event,
    // thus its color has to be retrieved from its owner to be displayed
    // as default event color instead of the first swatch of the palette
    retrieveCalendarColor() {
      this.calendarColor = null;
      this.calendarColorResolved = false;
      const owner = this.calendarOwner;
      if (this.event.color || (this.event.calendar && this.event.calendar.color) || !owner) {
        this.calendarColorResolved = true;
        return;
      }
      // When the parent component already retrieved the calendar, its color is
      // displayed without any additional request, thus without any flickering
      const ownerCalendar = this.ownerCalendar;
      if (ownerCalendar) {
        this.calendarColor = ownerCalendar.color || null;
        this.calendarColorResolved = true;
        return;
      }
      let ownerIdentityPromise = null;
      if (Number(owner.id)) {
        ownerIdentityPromise = Promise.resolve(owner);
      } else if (owner.remoteId && owner.providerId) {
        ownerIdentityPromise = this.$identityService.getIdentityByProviderIdAndRemoteId(owner.providerId, owner.remoteId);
      } else {
        // The calendar owner isn't determined yet, thus no color can be displayed
        // until it is, else the default color of the palette would be displayed first
        return;
      }
      ownerIdentityPromise
        .then(identity => identity && identity.id && this.$calendarService.getCalendars(0, 1, false, [Number(identity.id)]))
        .then(data => {
          const calendar = (data && data.calendars && data.calendars.length && data.calendars[0]) || null;
          // The color of a calendar that isn't created yet is the one that will
          // effectively be used once created, thus it can be displayed as well.
          // The owner is checked to discard an outdated result, in case the selected
          // calendar owner changed meanwhile
          if (calendar && this.calendarOwner === owner) {
            this.calendarColor = calendar.color;
          }
        })
        .catch(() => this.calendarColor = null)
        .finally(() => {
          if (this.calendarOwner === owner) {
            this.calendarColorResolved = true;
          }
        });
    },
    applyColor() {
      this.$set(this.event, 'color', this.newEventColor);
      this.menu = false;
    },
    closeMenu() {
      this.menu = false;
    },
  },
};
</script>
