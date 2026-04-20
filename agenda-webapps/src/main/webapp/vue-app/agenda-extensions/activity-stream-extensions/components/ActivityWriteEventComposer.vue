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
  <v-card
    id="writeEventComposerButton"
    :class="{'opacity-5': disableComposerButton}"
    class="mx-4 mt-3 px-6 py-3 card-border-radius"
    outlined
    flat
    hover
    :title="buttonTitle">
    <div
      class="d-flex flex-row align-center"
      @click="switchToEvent">
      <v-icon
        color="primary"
        size="39"
        style="min-height:50px">
        fas fa-calendar-plus
      </v-icon>
      <span class="font-weight-bold ms-5">
        {{ $t('agenda.composer.event.add.label') }}
      </span>
    </div>
  </v-card>
</template>
<script>
export default {
  props: {
    activityId: {
      type: String,
      default: null,
    },
    message: {
      type: String,
      default: null,
    },
    activityType: {
      type: Array,
      default: null,
    },
  },
  computed: {
    disableComposerButton() {
      return this.activityType?.length;
    },
    buttonTitle() {
      return this.disableComposerButton && this.$t('agenda.composer.event.disabled.title')
        || this.$t('agenda.composer.event.description');
    }
  },
  methods: {
    switchToEvent() {
      let url = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/news-editor?extensionType=event`;
      if (eXo.env.portal.spaceId) {
        url += `&spaceId=${eXo.env.portal.spaceId}&spaceName=${eXo.env.portal.spaceName}&type=draft`;
      }
      localStorage.setItem('exo-activity-composer-message', this.message || '');
      window.open(url, '_blank');
    },
  },
};
</script>
