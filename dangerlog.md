## Frontend issues and solutions

1. Django Built-in User for authentification does not support a is_seller field
    - Solution: Build a User-profile model to have a foreign key pointing to Users and a filed called is_seller

2. The actual table names in the postgres database is hidden by ORM, hence the backend does not know how to work with the database that frontend creates
    - Solution: Specify all table names with each model class's inner class Meta

3. When a purchase is made, the frontend should wait for an ack message from backend
    - Solution: We developed a function to build a socket to connect to backend, send the message and wait for ack with correct unique sequence number, and we specified a timeout of 5 seconds

4. Exact match for searching items is useless, nobody in real life use an exact match
    - Solution: use __icontains

5. We want to add a field seller to each items, so we can build a page to display all items listed by the current user, but we initialized some items, who should be the seller?
    - Solution: In apps.py, where all items get initialized, we add a function to initialize a default user and associate all items to this user

## Backend related issues

1. At first we discussed about the communication between the Ups and Amazon. The first issue is wether using one socket through whole communication or creating a new socket every time when we got new request or response. 
    - create new socket: We don't need to worry about different response or request using same buffer. However, if we have lot of response and request at the same time mey cause memory overflow, the scalability is very poor.

    - use the same socket: ervey request and response is using the same I/O buffer. We need make sure the length we read from buffer is correct and keep retry in a loop if we are reading other thread's response until we got our response.

2. Python Backend and Java Backend inconsistence. In Java, we can use the API provided by Grpc to read and write to the I/O stream. It will automatically