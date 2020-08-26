<template>
  <v-col class="ml-4">
    <v-tabs v-model="activeView">
      <v-tab
        dark
        @click="$root.$emit('switch-type', 'day')">
        {{ $t('agenda.label.viewDay') }}
      </v-tab>
      <v-tab
        dark
        @click="$root.$emit('switch-type', 'week')">
        {{ $t('agenda.label.viewWeek') }}
      </v-tab>
      <v-tab
        dark
        @click="$root.$emit('switch-type', 'month')">
        {{ $t('agenda.label.viewMonth') }}
      </v-tab>
    </v-tabs>
  </v-col>
</template>

<script>
export default {
  data: () => ({
    activeView:1
  })
}
</script>
