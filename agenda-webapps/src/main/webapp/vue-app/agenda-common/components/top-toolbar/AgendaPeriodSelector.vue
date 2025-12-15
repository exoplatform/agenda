<!--
 Copyright (C) 2025 eXo Platform SAS.

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
  <div class="d-flex">
    <v-btn
      max-width="36"
      max-height="36"
      icon
      @click="prevDate">
      <v-icon size="20" class="text-light-color">
        fa-chevron-left
      </v-icon>
    </v-btn>
    <v-btn
      max-width="36"
      max-height="36"
      icon
      @click="nextDate">
      <v-icon size="20" class="text-light-color">
        fa-chevron-right
      </v-icon>
    </v-btn>
    <div class="period-title text-uppercase my-auto ms-2">
      {{ periodTitle }}
    </div>
  </div>
</template>
<script>
export default {
  props: {
    periodTitle: {
      type: String,
      default: null
    },
  },
  methods: {
    nextDate() {
      this.$root.$emit('agenda-display-calendar-next');
    },
    prevDate() {
      this.$root.$emit('agenda-display-calendar-previous');
    }
  },  
};
</script>
