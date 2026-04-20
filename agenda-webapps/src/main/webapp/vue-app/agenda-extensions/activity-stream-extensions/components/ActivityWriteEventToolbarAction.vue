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
  <v-btn
    id="writeNewsBtnToolbar"
    :ripple="false"
    class="d-flex flex-row align-center py-2"
    text
    @click="switchToEvent">
    <v-icon
      color="primary"
      size="27">
      fas fa-calendar-plus
    </v-icon>
    <span class="font-weight-bold ms-5 dark-grey-color">
      {{ $t('agenda.composer.event.label') }}
    </span>
  </v-btn>
</template>
<script>
export default {
  props: {
    message: {
      type: String,
      default: null,
    },
    activityType: {
      type: Array,
      default: null,
    },
  },
  methods: {
    switchToEvent() {
      let url = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/news-editor?extensionType=event`;
      if (eXo.env.portal.spaceId) {
        url += `&spaceId=${eXo.env.portal.spaceId}&spaceName=${eXo.env.portal.spaceName}&type=draft`;
      }
      localStorage.setItem('exo-activity-composer-message', this.message || '');
      window.open(url, '_blank');
    }
  },
};
</script>
