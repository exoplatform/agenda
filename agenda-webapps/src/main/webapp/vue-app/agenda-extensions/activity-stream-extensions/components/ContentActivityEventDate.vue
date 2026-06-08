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
    v-if="eventStartDate"
    :title="$t('content.activity.event.date.title')"
    class="d-flex text-subtitle my-auto">
    <v-icon
      size="16"
      class="icon-default-color me-2">
      fas fa-calendar-alt
    </v-icon>
    <date-format
      :value="eventStartDate"
      :format="dateFormat" />
  </div>
</template>

<script>
export default {
  data() {
    return {
      event: null,
      dateFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      }
    };
  },
  props: {
    activity: {
      type: Object,
      default: null,
    },
  },
  created() {
    this.init();
  },
  computed: {
    eventId() {
      return this.activity.news?.parameters?.eventId;
    },
    eventStartDate() {
      return this.event?.start && new Date(this.event.start);
    }
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