<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <div
    :class="parentExtraClass"
    class="d-flex align-center position-relative width-fit-content">
    <template v-for="(date, i) in dates">
      <v-sheet
        :key="i"
        :width="chipSize"
        :height="chipSize"
        class="d-flex flex-column align-center"
        :class="[chipExtraClass, {
          'primary white--text': i === 0 ,
          'background-grey-primary text-color': i === 1,
          'rounded-tr': i === 1 || dates?.length === 1
        }]">
        <span class="font-weight-bold">
          {{ date.day }}
        </span>
        <span class="text-subtitle-font-size text-capitalize">
          {{ date.month }}
        </span>
      </v-sheet>
      <v-icon
        v-if="i === 0 && dates.length > 1"
        :key="`chevron-${i}`"
        :size="chipArrowSize"
        class="absolute-all-center white-background border-radius-circle text-color">
        mdi-chevron-right
      </v-icon>
    </template>
  </div>
</template>

<script>
export default {
  data() {
    return {
      event: null,
      locale: eXo?.env?.portal?.language || 'en'
    };
  },
  props: {
    parameters: {
      type: Object,
      default: null,
    },
    chipSize: {
      type: Number,
      default: 48,
    },
    chipExtraClass: {
      type: String,
      default: 'text-font-size pa-2'
    },
    chipArrowSize: {
      type: Number,
      default: 16
    },
    parentExtraClass: {
      type: String,
      default: ''
    }
  },
  created() {
    this.init();
  },
  computed: {
    eventId() {
      return this.parameters?.eventId;
    },
    dates() {
      if (!this.event) {
        return [];
      }
      const start = new Date(this.event.start);
      const end = new Date(this.event.end);
      const isSameDay = start.toDateString() === end.toDateString();
      const datesToShow = isSameDay ? [this.event.start] : [this.event.start, this.event.end];
      return datesToShow.map(date => ({
        day: new Date(date).toLocaleString(this.locale, { day: '2-digit' }),
        month: new Date(date).toLocaleString(this.locale, { month: 'short' }),
      }));
    },
  },
  methods: {
    async init() {
      this.loading = true;
      this.event = null;
      try {
        this.event = await this.$eventService.getEventById(this.eventId);
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>