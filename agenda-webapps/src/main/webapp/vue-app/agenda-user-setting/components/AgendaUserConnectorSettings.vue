<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        <div>
          {{ $t('agenda.connectYourPersonalAgenda') }}
        </div>
      </v-list-item-title>
      <v-list-item-subtitle class="my-3 text-sub-title font-italic">
        <agenda-connector-status :connectors="connectors">
          <template slot="connectButton">
            {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
          </template>
        </agenda-connector-status>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        small
        icon
        @click="openDrawer">
        <i class="uiIconEdit uiIconLightBlue pb-2"></i>
      </v-btn>
    </v-list-item-action>
    <agenda-connectors-drawer :connectors="enabledConnectors" />
    <agenda-connector
      :settings="settings"
      :connectors="connectors"
      auto-connect
      @connectors-loaded="connectors = $event" />
  </v-list-item>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    connectors: [],
  }),
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.enabled) || [];
    },
  },
  methods: {
    openDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>