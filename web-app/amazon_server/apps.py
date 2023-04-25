from django.apps import AppConfig
from django.db.models.signals import post_migrate

# This function will check whether there are default products, and will create if not.
def default_items():
    from .models import Item
    if Item.objects.all().count() == 0:
        # at the first time, we should insert some default data
        Item.objects.create(
            description="apple",        
        )
        Item.objects.create(
            description="computer",        
        )
        Item.objects.create(
            description="exam solutions",        
        )

def default_warehouse():
    from .models import WareHouse
    # create 10 warehouse
    for x, y in zip(range(10, 110, 10), range(10, 110, 10)):
        WareHouse.objects.create(x_cord=x, y_cord=y)

def migrate_callback(sender, **kwargs):
    default_items()
    default_warehouse()

class AmazonServerConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'amazon_server'
    def ready(self):
        post_migrate.connect(migrate_callback, sender=self)