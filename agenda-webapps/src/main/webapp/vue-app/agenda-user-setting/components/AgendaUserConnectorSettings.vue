<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        <div :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width skeleton-text-height my-2'">
          {{ skeleton && '&nbsp;' || $t('agenda.connectYourPersonalAgenda') }}
        </div>
      </v-list-item-title>
      <v-list-item-subtitle class="my-3 text-sub-title font-italic">
        <div
          v-if="connectedAccountName">
          <v-avatar tile size="24">
            <img
              :alt="connectedAccount.name"
              :src="connectedAccountIconSource">
          </v-avatar>
          <a
            class="mx-2"
            @click="openDrawer">
            {{ connectedAccountName }}
          </a>
        </div>
        <div
          v-else
          :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width-small skeleton-text-height-fine my-2'">
          {{ skeleton && '&nbsp;' || $t('agenda.connectYourPersonalAgendaSubTitle') }}
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        :class="skeleton && 'skeleton-background'"
        small
        icon
        @click="openDrawer">
        <i v-if="!skeleton" class="uiIconEdit uiIconLightBlue pb-2"></i>
      </v-btn>
    </v-list-item-action>
    <agenda-user-connected-account-drawer
      :connected-account="connectedAccount"
      :connectors="connectors" />
    <agenda-connector :connectors="connectors" />
  </v-list-item>
</template>

<script>
export default {
  props: {
    skeleton: {
      type: Boolean,
      default: true,
    },
  },
  data: () => ({
    connectedAccount: {},
    connectors: [],
  }),
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.refresh);
    this.refresh();
    this.$root.$on('agenda-connector-loaded', connectors => {
      this.connectors = connectors;
    });
  },
  methods: {
    refresh() {
      this.$calendarService.getAgendaConnectorsSettings().then(connectorSettings => {
        if (connectorSettings && connectorSettings.value) {
          this.connectedAccount = JSON.parse(connectorSettings.value);
        }
      });
    },
    openDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    }
  }
};
</script>