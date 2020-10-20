<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        <div :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width skeleton-text-height my-2'">
          {{ skeleton && '&nbsp;' || $t('agenda.connectYourPersonalAgenda') }}
        </div>
      </v-list-item-title>
      <v-list-item-subtitle class="text-sub-title">
        <div :class="skeleton && 'skeleton-background skeleton-border-radius skeleton-text-width-small skeleton-text-height-fine my-2'">
          {{ connectedAccountName || '' }}
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
    <agenda-user-connected-account-drawer />
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
    connectedAccount: null,
  }),
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.name || '';
    },
    connectedAccountIconClass() {
      return this.connectedAccount && this.connectedAccount.iconClass || '';
    },
  },
  methods: {
    openDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open', this.connectedAccount);
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>