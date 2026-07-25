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
    bottom
    left>
    <template #activator="{ on, attrs }">
      <div
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
  },
  data: () => ({
    menu: false,
    newEventColor: null,
  }),
  computed: {
    eventColor() {
      return this.event.color || (this.event.calendar && this.event.calendar.color) || this.$agendaUtils.EVENT_COLOR_SWATCHES[0][0];
    },
    eventColorMenuId() {
      return `eventColorMenu${this._uid}`;
    },
  },
  watch: {
    menu(opened) {
      if (opened) {
        this.newEventColor = this.eventColor;
      }
    },
  },
  methods: {
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
