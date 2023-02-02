# Getting Started with Azure Databricks Log4J to Azure Data Explorer

Connect your Azure Databricks cluster log4j output to the Azure Data Explorer Log4j Appender. This will help you get your logs to a centralized location such as Azure Data Explorer.
Here we will be using the [log4j2-ADX connector](https://github.com/Azure/azure-kusto-log4j) to send log data from Azure Databricks to Azure Data Explorer.

### Configuration Steps : Azure Data Explorer

- Create Azure Data Explorer cluster and database from [here](https://learn.microsoft.com/en-us/azure/data-explorer/create-cluster-database-portal).
- Create Azure Active Directory App registration and grant it permissions to the database from [here](https://learn.microsoft.com/en-us/azure/data-explorer/provision-azure-ad-app). (don't forget to save the app key and the application ID for later.)
- Create a table in Azure Data Explorer which will be used to store log data. In this example, we have created a table with the name for example, "log4jTest".

```sql
.create table log4jTest (timenanos:long,timemillis:long,level:string,threadid:string,threadname:string,threadpriority:int,formattedmessage:string,loggerfqcn:string,loggername:string,marker:string,thrownproxy:string,source:string,contextmap:string,contextstack:string)
```
- Create a mapping for the table created in Azure Data Explorer. In this example, we have created a csv mapping with the name, "log4jCsvTestMapping".

```sql
.create table log4jTest ingestion csv mapping 'log4jCsvTestMapping' '[{"Name":"timenanos","DataType":"","Ordinal":"0","ConstValue":null},{"Name":"timemillis","DataType":"","Ordinal":"1","ConstValue":null},{"Name":"level","DataType":"","Ordinal":"2","ConstValue":null},{"Name":"threadid","DataType":"","Ordinal":"3","ConstValue":null},{"Name":"threadname","DataType":"","Ordinal":"4","ConstValue":null},{"Name":"threadpriority","DataType":"","Ordinal":"5","ConstValue":null},{"Name":"formattedmessage","DataType":"","Ordinal":"6","ConstValue":null},{"Name":"loggerfqcn","DataType":"","Ordinal":"7","ConstValue":null},{"Name":"loggername","DataType":"","Ordinal":"8","ConstValue":null},{"Name":"marker","DataType":"","Ordinal":"9","ConstValue":null},{"Name":"thrownproxy","DataType":"","Ordinal":"10","ConstValue":null},{"Name":"source","DataType":"","Ordinal":"11","ConstValue":null},{"Name":"contextmap","DataType":"","Ordinal":"12","ConstValue":null},{"Name":"contextstack","DataType":"","Ordinal":"13","ConstValue":null}]'
```

### Configuration Steps : Azure Databricks

If we modify the default log4j2.properties of the driver when executor is a local configuration, it will be lost at every restart of the cluster. Therefore, in order to preserve the configuration, an init script should be configured.
Further details about the init scripts of Azure Databricks cluster can be found [here](https://docs.databricks.com/clusters/init-scripts.html).

- Create Databricks workspace in Azure
- Create a cluster in Databricks (any size and shape is fine)
- Start the cluster and execute the following python script in the databricks notebook.

```python
dbutils.fs.mkdirs("dbfs:/databricks/scripts/")
```
- Execute in databricks notebook the python script in resources/Cluster-Init-Script.py which will download the [log4j2-ADX connector](https://github.com/Azure/azure-kusto-log4j) and adds custom appender to the log4j2.properties file in the driver, executor, as well as master-worker nodes.
- Navigate to the Cluster Configuration -> Advanced Options -> Init Scripts.
- Add the init script for "dbfs:/databricks/scripts/init-log4j-kusto-logging.sh"

![alt text](https://techcommunity.microsoft.com/t5/image/serverpage/image-id/435868i88B7CB8465AF112B/image-size/large?v=v2&px=999)

- After the above changes please restart the cluster 

### Verification Steps
- After the cluster restart logs will be automatically pushed to ADX. Also application loggers can be configured to push logs to ADX as well.
- Now we can query the log4JTest table in ADX and check for the data ingested.

Please refer to the following links
[Kusto Log4J Connector](https://github.com/Azure/azure-kusto-log4j)
[Microsoft TechCommunity Blog](https://techcommunity.microsoft.com/t5/blogs/blogworkflowpage/blog-id/AzureDataExplorer/article-id/378)

If you don't have a Microsoft Azure subscription you can get a FREE trial
account [here](http://go.microsoft.com/fwlink/?LinkId=330212)

---

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
