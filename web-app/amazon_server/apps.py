from django.apps import AppConfig
from django.db.models.signals import post_migrate
from PIL import Image
# This function will check whether there are default users, and will create two if not.
def default_users():
    from django.contrib.auth.models import User
    from .models import UserProfile
    try:
        User.objects.get(username="chenjie")
    except User.DoesNotExist:
        mini_amazon = User.objects.create(
            username="chenjie",
            email="cy141@duke.edu",
            is_superuser=False
        )
        mini_amazon.set_password("YxYcj123!")
        user_profile = UserProfile.objects.get(user=mini_amazon)
        user_profile.is_seller = True
        user_profile.save()
        

# This function will check whether there are default products, and will create if not.
def default_items():
    from .models import Item
    from django.contrib.auth.models import User
    user = User.objects.get(username="chenjie")
    if Item.objects.all().count() == 0:
        # at the first time, we should insert some default data
        Item.objects.create(
            description="apple", seller = user 
        )
        Item.objects.create(
            description="computer", seller = user     
        )
        Item.objects.create(
            description="exam solutions", seller = user   
        )

def default_warehouse():
    from .models import WareHouse
    # create 10 warehouse
    for x, y in zip(range(10, 110, 10), range(10, 110, 10)):
        WareHouse.objects.create(x_cord=x, y_cord=y)

def migrate_callback(sender, **kwargs):
    default_users()
    default_items()
    default_warehouse()

class AmazonServerConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'amazon_server'
    def ready(self):
        post_migrate.connect(migrate_callback, sender=self)