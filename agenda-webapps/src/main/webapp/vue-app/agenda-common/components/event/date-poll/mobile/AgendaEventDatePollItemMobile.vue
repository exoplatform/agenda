<template>
  <v-list>
    <v-list-item-group
      v-model="selected"
      color="primary">
      <v-list-item
        v-for="(dateOption, index) in dateOptions"
        :key="index">
        <agenda-event-date-option-vote
          class="my-auto"
          :date-option="dateOption"
          :vote="vote"
          :disabled="disabled"
          :voter="voter" />
        <agenda-event-date-option-period-mobile
          :date-option="dateOption"
          class="text--primary my-auto" />
        <v-list-item-content
          class="text--primary my-auto"
          @click="$root.$emit('selected-date-option',dateOption)">
          <agenda-event-date-option-voter-mobile :date-option="dateOption" />
        </v-list-item-content>
        <div class="my-auto">
          <v-btn
            :title="$t('agenda.finalDate')"
            icon
            right
            fab
            x-small>
            <v-icon color="#f8b441">fa-trophy</v-icon>
          </v-btn>
        </div>
      </v-list-item>
    </v-list-item-group>
  </v-list>
</template>

<script>
export default {
  props: {
    dateOptions: {
      type: Array,
      default: () => null
    },
  },
};
</script>