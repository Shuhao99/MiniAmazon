# Generated by Django 4.1.5 on 2023-04-23 20:38

from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('amazon_server', '0004_shoppingcartitem_delete_shoppingcart'),
    ]

    operations = [
        migrations.AlterModelTable(
            name='item',
            table='item',
        ),
        migrations.AlterModelTable(
            name='order',
            table='order',
        ),
        migrations.AlterModelTable(
            name='ordered_items',
            table='ordered_items',
        ),
        migrations.AlterModelTable(
            name='shoppingcartitem',
            table='shoppingcartitem',
        ),
        migrations.AlterModelTable(
            name='userprofile',
            table='userprofile',
        ),
        migrations.AlterModelTable(
            name='warehouse',
            table='warehouse',
        ),
    ]
